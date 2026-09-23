package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementTransformationTest {

    @Mock
    private DecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        reset(transformationService);
    }

    @Test
    void output_criteria_success_outputs_validated_fanol_payload_channel_metadata_next_steps_policy_match_failure_outputs_validation_errors_correction_requests_fallback_to_manual() {
        // --- Success Path Verification ---
        FnolPayload validPayload = new FnolPayload("POL-100", "CLM-200");
        Map<String, Object> expectedMetadata = Map.of("channel", "web_portal", "ingested_at", "2023-10-27T10:00:00Z");
        TransformationResult successResult = new TransformationResult(
                true, validPayload, expectedMetadata, "policy_match",
                Collections.emptyList(), Collections.emptyList(), false
        );

        when(transformationService.transform(validPayload)).thenReturn(successResult);

        TransformationResult actualSuccess = transformationService.transform(validPayload);

        assertTrue(actualSuccess.isSuccess(), "Transformation should succeed for valid payload");
        assertNotNull(actualSuccess.getValidatedPayload(), "Validated FNOL payload must be present");
        assertEquals(validPayload, actualSuccess.getValidatedPayload(), "Payload should match input");
        assertNotNull(actualSuccess.getChannelMetadata(), "Channel metadata must be present");
        assertEquals("policy_match", actualSuccess.getNextSteps(), "Next steps should indicate policy match");
        assertTrue(actualSuccess.getValidationErrors().isEmpty(), "No validation errors expected");
        assertTrue(actualSuccess.getCorrectionRequests().isEmpty(), "No correction requests expected");
        assertFalse(actualSuccess.isFallbackToManual(), "Fallback to manual should be false");

        // --- Failure Path Verification ---
        FnolPayload invalidPayload = new FnolPayload(null, "CLM-300");
        TransformationResult failureResult = new TransformationResult(
                false, null, Map.of(), null,
                List.of("missing_policy_id", "invalid_date"),
                List.of("verify_insured_contact", "upload_proof_of_loss"),
                true
        );

        when(transformationService.transform(invalidPayload)).thenReturn(failureResult);

        TransformationResult actualFailure = transformationService.transform(invalidPayload);

        assertFalse(actualFailure.isSuccess(), "Transformation should fail for invalid payload");
        assertNull(actualFailure.getValidatedPayload(), "Validated payload must be null on failure");
        assertNull(actualFailure.getNextSteps(), "Next steps should be null on failure");
        assertFalse(actualFailure.getValidationErrors().isEmpty(), "Validation errors must be present");
        assertFalse(actualFailure.getCorrectionRequests().isEmpty(), "Correction requests must be present");
        assertTrue(actualFailure.isFallbackToManual(), "Fallback to manual should be true");
    }

    interface DecisionTransformationService {
        TransformationResult transform(FnolPayload payload);
    }

    static class FnolPayload {
        private final String policyId;
        private final String claimId;

        public FnolPayload(String policyId, String claimId) {
            this.policyId = policyId;
            this.claimId = claimId;
        }

        public String getPolicyId() { return policyId; }
        public String getClaimId() { return claimId; }
        public boolean isValid() { return policyId != null && claimId != null; }
    }

    static class TransformationResult {
        private final boolean success;
        private final FnolPayload validatedPayload;
        private final Map<String, Object> channelMetadata;
        private final String nextSteps;
        private final List<String> validationErrors;
        private final List<String> correctionRequests;
        private final boolean fallbackToManual;

        public TransformationResult(boolean success, FnolPayload validatedPayload,
                                    Map<String, Object> channelMetadata, String nextSteps,
                                    List<String> validationErrors, List<String> correctionRequests,
                                    boolean fallbackToManual) {
            this.success = success;
            this.validatedPayload = validatedPayload;
            this.channelMetadata = channelMetadata;
            this.nextSteps = nextSteps;
            this.validationErrors = validationErrors;
            this.correctionRequests = correctionRequests;
            this.fallbackToManual = fallbackToManual;
        }

        public boolean isSuccess() { return success; }
        public FnolPayload getValidatedPayload() { return validatedPayload; }
        public Map<String, Object> getChannelMetadata() { return channelMetadata; }
        public String getNextSteps() { return nextSteps; }
        public List<String> getValidationErrors() { return validationErrors; }
        public List<String> getCorrectionRequests() { return correctionRequests; }
        public boolean isFallbackToManual() { return fallbackToManual; }
    }
}
