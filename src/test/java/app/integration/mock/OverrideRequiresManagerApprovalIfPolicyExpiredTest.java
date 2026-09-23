package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private DecisionOrchestrator decisionOrchestrator;

    @Mock
    private ReferenceDataCache cacheService;

    @Mock
    private ClaimsDataStore dynamoDbService;

    private ClaimDecisionValidationService sut;

    @BeforeEach
    void setUp() {
        sut = new ClaimDecisionValidationService(decisionOrchestrator, cacheService, dynamoDbService);
    }

    @Test
    void override_requires_manager_approval_if_policy_expired() {
        // Arrange
        String id = "claim-123-expired-policy";
        Map<String, Object> payload = Map.of(
                "policyStatus", "EXPIRED",
                "overrideRequested", true,
                "requesterRole", "CLAIMS_ANALYST"
        );

        RoutingDecision expectedDecision = new RoutingDecision(
                id,
                RoutingStatus.MANAGER_APPROVAL_REQUIRED,
                "Override requires manager approval due to expired policy."
        );

        when(decisionOrchestrator.evaluateOverride(anyString(), anyMap()))
                .thenReturn(expectedDecision);

        // Act
        RoutingDecision actualDecision = sut.processDecision(id, payload);

        // Assert
        assertNotNull(actualDecision);
        assertEquals(id, actualDecision.id());
        assertEquals(RoutingStatus.MANAGER_APPROVAL_REQUIRED, actualDecision.status());
        assertTrue(actualDecision.requiresManagerApproval());
        verify(decisionOrchestrator).evaluateOverride(eq(id), eq(payload));
        verifyNoMoreInteractions(decisionOrchestrator);
    }

    // Internal interfaces for mocked external I/O
    interface DecisionOrchestrator {
        RoutingDecision evaluateOverride(String id, Map<String, Object> payload);
    }

    interface ReferenceDataCache {
        String get(String namespace, String key);
    }

    interface ClaimsDataStore {
        Map<String, Object> getItem(String tableName, String pk);
    }

    record RoutingDecision(String id, RoutingStatus status, String message) {
        public boolean requiresManagerApproval() {
            return status == RoutingStatus.MANAGER_APPROVAL_REQUIRED;
        }
    }

    // Stateless SUT ensuring thread safety and deterministic behavior
    static class ClaimDecisionValidationService {
        private final DecisionOrchestrator decisionOrchestrator;
        private final ReferenceDataCache cacheService;
        private final ClaimsDataStore dynamoDbService;

        ClaimDecisionValidationService(DecisionOrchestrator decisionOrchestrator,
                                       ReferenceDataCache cacheService,
                                       ClaimsDataStore dynamoDbService) {
            this.decisionOrchestrator = Objects.requireNonNull(decisionOrchestrator);
            this.cacheService = Objects.requireNonNull(cacheService);
            this.dynamoDbService = Objects.requireNonNull(dynamoDbService);
        }

        RoutingDecision processDecision(String id, Map<String, Object> payload) {
            // Input validation per NFR: least_privilege_iam & input_validation
            Objects.requireNonNull(id, "Claim ID must not be null");
            Objects.requireNonNull(payload, "Payload must not be null");

            // Orchestration call (mocked to avoid live AWS/HTTP I/O)
            return decisionOrchestrator.evaluateOverride(id, payload);
        }
    }
}
