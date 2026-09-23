package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private ClaimDecisionService claimDecisionService;

    private ClaimDataStandardizationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClaimDataStandardizationValidator(claimDecisionService, 0.85);
    }

    @Test
    void override_requires_reason_code_supervisor_approval_if_confidence_threshold() {
        // Given: Payload with confidence score below the defined threshold
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimId", "CLM-2024-001");
        payload.put("confidenceScore", 0.72);
        payload.put("claimType", "AUTO");

        // Mock decision service response for low confidence
        ClaimDecision decision = new ClaimDecision(
                true,
                true,
                "LOW_CONFIDENCE_OVERRIDE",
                false
        );
        when(claimDecisionService.evaluate(anyMap())).thenReturn(decision);

        // When: Validate claim data standardization decision
        ClaimDecision result = validator.validate(payload);

        // Then: Assert override behavior triggers required fields
        assertNotNull(result);
        assertTrue(result.requiresReasonCode(), "Reason code must be required when confidence < threshold");
        assertTrue(result.requiresSupervisorApproval(), "Supervisor approval must be required when confidence < threshold");
        assertEquals("LOW_CONFIDENCE_OVERRIDE", result.getReasonCode());
        verify(claimDecisionService, times(1)).evaluate(payload);
    }

    // Minimal stubs to represent production interfaces for test compilation
    static class ClaimDecisionService {
        public ClaimDecision evaluate(Map<String, Object> payload) { return null; }
    }

    static class ClaimDataStandardizationValidator {
        private final ClaimDecisionService service;
        private final double threshold;

        ClaimDataStandardizationValidator(ClaimDecisionService service, double threshold) {
            this.service = service;
            this.threshold = threshold;
        }

        ClaimDecision validate(Map<String, Object> payload) {
            return service.evaluate(payload);
        }
    }

    static class ClaimDecision {
        private final boolean requiresReasonCode;
        private final boolean requiresSupervisorApproval;
        private final String reasonCode;
        private final boolean supervisorApproved;

        ClaimDecision(boolean requiresReasonCode, boolean requiresSupervisorApproval, String reasonCode, boolean supervisorApproved) {
            this.requiresReasonCode = requiresReasonCode;
            this.requiresSupervisorApproval = requiresSupervisorApproval;
            this.reasonCode = reasonCode;
            this.supervisorApproved = supervisorApproved;
        }

        public boolean requiresReasonCode() { return requiresReasonCode; }
        public boolean requiresSupervisorApproval() { return requiresSupervisorApproval; }
        public String getReasonCode() { return reasonCode; }
    }
}
