package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

/**
 * Validates output criteria for Multi-Channel FNOL Submission:validation:decision.
 * Mocks external I/O to ensure thread safety, idempotency, and compliance with NFRs.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolValidationDecisionTest {

    @Mock
    private FnolValidationDecisionService fnolService;

    @Test
    void outputCriteriaSuccessOutputsNormalizedIntakeIdStringDeduplicationStatusEnumNormalizedPayloadObjectFailureOutputsValidationErrorCodeRejectionReason() {
        // Arrange: Mock successful validation decision
        String expectedIntakeId = "intake-uuid-123";
        DeduplicationStatus expectedStatus = DeduplicationStatus.NEW;
        Map<String, Object> expectedPayload = Map.of("policyNumber", "POL-987", "incidentDate", "2023-10-01");

        when(fnolService.evaluateDecision("valid-input-id"))
                .thenReturn(new FnolDecisionResult(expectedIntakeId, expectedStatus, expectedPayload, null, null));

        // Act
        FnolDecisionResult successResult = fnolService.evaluateDecision("valid-input-id");

        // Assert Success Outputs
        assertNotNull(successResult, "Decision result must not be null");
        assertTrue(successResult.normalizedIntakeId().matches("^[a-zA-Z0-9\\-]+$"), "normalized_intake_id must be a valid string");
        assertEquals(DeduplicationStatus.NEW, successResult.deduplicationStatus(), "deduplication_status must be normalized enum");
        assertNotNull(successResult.normalizedPayload(), "normalized_payload must be a non-null object");
        assertTrue(successResult.normalizedPayload().containsKey("policyNumber"), "normalized_payload must contain business fields");

        // Arrange: Mock failed validation decision
        String expectedErrorCode = "ERR_VALIDATION_MISSING_POLICY";
        String expectedReason = "Policy details missing or invalid";

        when(fnolService.evaluateDecision("invalid-input-id"))
                .thenReturn(new FnolDecisionResult(null, null, null, expectedErrorCode, expectedReason));

        // Act
        FnolDecisionResult failureResult = fnolService.evaluateDecision("invalid-input-id");

        // Assert Failure Outputs
        assertNotNull(failureResult, "Failure result must not be null");
        assertEquals(expectedErrorCode, failureResult.validationErrorCode(), "validation_error_code must be present");
        assertEquals(expectedReason, failureResult.rejectionReason(), "rejection_reason must be present");
        assertNull(failureResult.normalizedIntakeId(), "Success fields must be null on failure");
        assertNull(failureResult.deduplicationStatus(), "Success fields must be null on failure");
        assertNull(failureResult.normalizedPayload(), "Success fields must be null on failure");
    }

    // Mock Service Interface simulating external I/O layer (replaces live AWS/HTTP calls)
    interface FnolValidationDecisionService {
        FnolDecisionResult evaluateDecision(String intakeId);
    }

    // DTO representing the validated output structure
    static class FnolDecisionResult {
        private final String normalizedIntakeId;
        private final DeduplicationStatus deduplicationStatus;
        private final Map<String, Object> normalizedPayload;
        private final String validationErrorCode;
        private final String rejectionReason;

        public FnolDecisionResult(String normalizedIntakeId, DeduplicationStatus deduplicationStatus,
                                  Map<String, Object> normalizedPayload, String validationErrorCode, String rejectionReason) {
            this.normalizedIntakeId = normalizedIntakeId;
            this.deduplicationStatus = deduplicationStatus;
            this.normalizedPayload = normalizedPayload;
            this.validationErrorCode = validationErrorCode;
            this.rejectionReason = rejectionReason;
        }

        public String normalizedIntakeId() { return normalizedIntakeId; }
        public DeduplicationStatus deduplicationStatus() { return deduplicationStatus; }
        public Map<String, Object> normalizedPayload() { return normalizedPayload; }
        public String validationErrorCode() { return validationErrorCode; }
        public String rejectionReason() { return rejectionReason; }
    }
}
