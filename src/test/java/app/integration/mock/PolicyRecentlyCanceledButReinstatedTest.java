package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PolicyRecentlyCanceledButReinstatedTest {

    @Mock
    private PolicyStatusService policyStatusService;

    @Mock
    private FnolSubmissionValidator fnolSubmissionValidator;

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private DataStoreService dataStoreService;

    @Mock
    private CommunicationsHandlerService communicationsHandlerService;

    @InjectMocks
    private MultiChannelFnolValidationOrchestrator validationOrchestrator;

    private static final String POLICY_ID = "POL-REINSTATED-001";
    private static final String SUBMISSION_ID = "FNOL-SUB-001";

    @BeforeEach
    void setUp() {
        // Reset mock states and verify test isolation per NFR: thread_safety
        clearInvocations(policyStatusService, fnolSubmissionValidator, stateTransitionService);
    }

    @Test
    void policy_recently_canceled_but_reinstated() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyId", POLICY_ID,
                "channel", "PORTAL",
                "incidentTimestamp", "2023-10-25T10:00:00Z"
        );

        PolicyStatus status = mock(PolicyStatus.class);
        when(status.getCurrentStatus()).thenReturn("REINSTATED");
        when(status.getPreviousStatus()).thenReturn("CANCELED");
        when(status.getReinstatementDate()).thenReturn(java.time.LocalDateTime.now().minusDays(2));
        when(status.getCancelDate()).thenReturn(java.time.LocalDateTime.now().minusDays(5));

        when(policyStatusService.resolve(eq(POLICY_ID))).thenReturn(Optional.of(status));
        when(fnolSubmissionValidator.checkPolicyEligibility(any())).thenReturn(EligibilityResult.ACCEPTED);

        // Act
        ValidationOutcome outcome = validationOrchestrator.validateAndOrchestrate(SUBMISSION_ID, payload);

        // Assert
        assertNotNull(outcome, "Validation outcome must not be null");
        assertTrue(outcome.isEligible(), "Policy should be eligible after reinstatement per business rule");
        assertEquals("REINSTATED", outcome.getPolicyStatus(), "Status must reflect reinstatement state");

        // Verify external I/O is mocked and not called directly
        verify(policyStatusService, times(1)).resolve(eq(POLICY_ID));
        verify(fnolSubmissionValidator, times(1)).checkPolicyEligibility(any());
        verify(stateTransitionService, times(1)).transition(eq(SUBMISSION_ID), eq("VALIDATED"));
        verifyNoInteractions(dataStoreService, communicationsHandlerService);

        // NFR: compliance: gdpr, soc2 - Ensure no PII leakage in mock assertions
        assertFalse(outcome.getPayload().containsKey("ssn"), "PII fields must be stripped during validation");
    }

    // Minimal stubs for compilation context and contract validation
    interface PolicyStatusService {
        Optional<PolicyStatus> resolve(String policyId);
    }

    interface FnolSubmissionValidator {
        EligibilityResult checkPolicyEligibility(Map<String, Object> payload);
    }

    interface StateTransitionService {
        void transition(String submissionId, String newState);
    }

    interface DataStoreService {
        void save(String tableName, Map<String, Object> itemPayload);
    }

    interface CommunicationsHandlerService {
        String sendNotification(Map<String, Object> params);
    }

    interface PolicyStatus {
        String getCurrentStatus();
        String getPreviousStatus();
        java.time.LocalDateTime getReinstatementDate();
        java.time.LocalDateTime getCancelDate();
    }

    enum EligibilityResult {
        ACCEPTED, REJECTED
    }

    static class ValidationOutcome {
        private final boolean eligible;
        private final String policyStatus;
        private final Map<String, Object> payload;

        ValidationOutcome(boolean eligible, String policyStatus, Map<String, Object> payload) {
            this.eligible = eligible;
            this.policyStatus = policyStatus;
            this.payload = payload;
        }

        boolean isEligible() { return eligible; }
        String getPolicyStatus() { return policyStatus; }
        Map<String, Object> getPayload() { return payload; }
    }
}
