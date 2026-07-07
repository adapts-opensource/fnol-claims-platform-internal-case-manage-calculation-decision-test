package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Domain stubs for test isolation
class PolicySnapshot {
    String policyId;
    String insuredId;
    String currentState;
    Instant lastTransitionedAt;

    PolicySnapshot(String policyId, String insuredId) {
        this.policyId = policyId;
        this.insuredId = insuredId;
        this.currentState = "ACTIVE";
    }
}

interface PolicyPersistenceGateway {
    PolicySnapshot fetchPolicy(String policyId);
    void persistStateTransition(PolicySnapshot snapshot);
}

interface InsuredNotificationGateway {
    void notifyInsured(String targetAddress, String subject, String payload);
}

class StateTransitionOrchestrator {
    private final PolicyPersistenceGateway persistence;
    private final InsuredNotificationGateway notifications;

    StateTransitionOrchestrator(PolicyPersistenceGateway persistence, InsuredNotificationGateway notifications) {
        this.persistence = persistence;
        this.notifications = notifications;
    }

    void executeReassignment(String policyId, String newInsuredId) {
        PolicySnapshot snapshot = persistence.fetchPolicy(policyId);
        if (snapshot == null) {
            throw new IllegalArgumentException("Policy not found: " + policyId);
        }

        String previousInsured = snapshot.insuredId;
        snapshot.insuredId = newInsuredId;
        snapshot.currentState = "REASSIGNED";
        snapshot.lastTransitionedAt = Instant.now();

        persistence.persistStateTransition(snapshot);
        notifications.notifyInsured(
                previousInsured.toLowerCase() + "@newco-insurance.com",
                "Policy Reassignment Confirmation",
                "{\"policyId\":\"" + policyId + "\",\"newInsuredId\":\"" + newInsuredId + "\"}"
        );
    }
}

public class PolicyReassignedToNewInsuredTest {

    @Mock
    private PolicyPersistenceGateway mockPersistence;
    @Mock
    private InsuredNotificationGateway mockNotifications;

    private StateTransitionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new StateTransitionOrchestrator(mockPersistence, mockNotifications);
    }

    @Test
    void policy_reassigned_to_new_insured() {
        // Arrange
        String policyId = "POL-98765";
        String oldInsuredId = "INS-001";
        String newInsuredId = "INS-002";
        PolicySnapshot initialSnapshot = new PolicySnapshot(policyId, oldInsuredId);
        when(mockPersistence.fetchPolicy(policyId)).thenReturn(initialSnapshot);

        // Act
        orchestrator.executeReassignment(policyId, newInsuredId);

        // Assert
        assertEquals(newInsuredId, initialSnapshot.insuredId, "Policy insured must be updated to new insured");
        assertEquals("REASSIGNED", initialSnapshot.currentState, "Policy state must transition to REASSIGNED");
        assertNotNull(initialSnapshot.lastTransitionedAt, "Transition timestamp must be recorded");

        verify(mockPersistence, times(1)).persistStateTransition(initialSnapshot);
        verify(mockNotifications, times(1)).notifyInsured(
                eq(oldInsuredId.toLowerCase() + "@newco-insurance.com"),
                eq("Policy Reassignment Confirmation"),
                argThat(payload -> payload.contains(policyId) && payload.contains(newInsuredId))
        );
    }
}
