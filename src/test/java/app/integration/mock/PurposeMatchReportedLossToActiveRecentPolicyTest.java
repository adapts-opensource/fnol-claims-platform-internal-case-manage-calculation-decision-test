package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeMatchReportedLossToActiveRecentPolicyTest {

    @Mock
    private PolicyMatcher policyMatcher;

    @Mock
    private CoverageContextValidator coverageValidator;

    @Mock
    private DataStoreService dataStoreService;

    @Mock
    private CommunicationsHandler communicationsHandler;

    @InjectMocks
    private FnolOrchestrationService fnolOrchestrationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection; no live AWS/HTTP calls are initialized
    }

    @Test
    void purpose_match_reported_loss_to_active_recent_policy_and_validate_coverage_context() {
        // Arrange: Simulate inbound FNOL payload matching the data model
        String submissionId = "fnol-sub-456";
        Map<String, Object> payload = Map.of(
            "policyNumber", "POL-9876",
            "lossDate", "2023-11-20",
            "coverageType", "AUTO_COMPREHENSIVE",
            "channel", "WEB_PORTAL"
        );

        PolicyRecord activePolicy = new PolicyRecord("POL-9876", "ACTIVE", "2023-01-01", "2024-01-01");
        when(policyMatcher.findActiveOrRecentPolicy("POL-9876")).thenReturn(Optional.of(activePolicy));
        when(coverageValidator.isCoverageActiveForLoss(activePolicy, "AUTO_COMPREHENSIVE", "2023-11-20")).thenReturn(true);

        // Act: Execute orchestration/validation flow
        ValidationResult result = fnolOrchestrationService.validateSubmission(submissionId, payload);

        // Assert: Verify policy match, coverage validation, state persistence, and NFR compliance
        assertNotNull(result, "Validation result must not be null");
        assertTrue(result.isValid(), "Submission must pass active/recent policy match and coverage validation");
        assertEquals(submissionId, result.getSubmissionId());
        assertEquals("VALIDATED", result.getState());

        verify(policyMatcher).findActiveOrRecentPolicy("POL-9876");
        verify(coverageValidator).isCoverageActiveForLoss(activePolicy, "AUTO_COMPREHENSIVE", "2023-11-20");
        verify(dataStoreService).saveStateTransition(eq(submissionId), any(Map.class));
        verifyNoInteractions(communicationsHandler); // Notification deferred until post-validation
    }

    // Minimal domain contracts for isolated mock testing
    interface PolicyMatcher { Optional<PolicyRecord> findActiveOrRecentPolicy(String policyNumber); }
    interface CoverageContextValidator { boolean isCoverageActiveForLoss(PolicyRecord policy, String coverageType, String lossDate); }
    interface DataStoreService { void saveStateTransition(String id, Map<String, Object> payload); }
    interface CommunicationsHandler { void sendNotification(String id, String status); }

    static class PolicyRecord {
        final String policyNumber, status, effectiveDate, expirationDate;
        PolicyRecord(String policyNumber, String status, String effectiveDate, String expirationDate) {
            this.policyNumber = policyNumber; this.status = status; this.effectiveDate = effectiveDate; this.expirationDate = expirationDate;
        }
    }

    static class ValidationResult {
        private final String submissionId;
        private final boolean valid;
        private final String state;
        ValidationResult(String submissionId, boolean valid, String state) {
            this.submissionId = submissionId; this.valid = valid; this.state = state;
        }
        String getSubmissionId() { return submissionId; }
        boolean isValid() { return valid; }
        String getState() { return state; }
    }

    class FnolOrchestrationService {
        private final PolicyMatcher policyMatcher;
        private final CoverageContextValidator coverageValidator;
        private final DataStoreService dataStoreService;
        private final CommunicationsHandler communicationsHandler;

        FnolOrchestrationService(PolicyMatcher policyMatcher, CoverageContextValidator coverageValidator, DataStoreService dataStoreService, CommunicationsHandler communicationsHandler) {
            this.policyMatcher = policyMatcher;
            this.coverageValidator = coverageValidator;
            this.dataStoreService = dataStoreService;
            this.communicationsHandler = communicationsHandler;
        }

        ValidationResult validateSubmission(String submissionId, Map<String, Object> payload) {
            String policyNum = (String) payload.get("policyNumber");
            String lossDate = (String) payload.get("lossDate");
            String coverageType = (String) payload.get("coverageType");

            // Input validation NFR
            if (policyNum == null || lossDate == null || coverageType == null) {
                return new ValidationResult(submissionId, false, "INVALID_INPUT");
            }

            // Policy matching NFR: active/recent lookup
            Optional<PolicyRecord> policyOpt = policyMatcher.findActiveOrRecentPolicy(policyNum);
            if (policyOpt.isEmpty()) {
                return new ValidationResult(submissionId, false, "POLICY_NOT_FOUND");
            }

            PolicyRecord policy = policyOpt.get();
            // Coverage context validation NFR
            boolean coverageValid = coverageValidator.isCoverageActiveForLoss(policy, coverageType, lossDate);
            if (!coverageValid) {
                return new ValidationResult(submissionId, false, "COVERAGE_INVALID");
            }

            // Persist state transition to DynamoDB mock
            dataStoreService.saveStateTransition(submissionId, payload);
            return new ValidationResult(submissionId, true, "VALIDATED");
        }
    }
}
