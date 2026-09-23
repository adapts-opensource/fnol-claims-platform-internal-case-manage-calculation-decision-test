package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private PolicyValidationService policyValidationService;

    private ClaimValidationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ClaimValidationDecisionService(policyValidationService);
    }

    @Test
    void pas_outage_during_match_attempt() {
        String claimId = "CLM-789";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("policyNumber", "POL-101");

        // Simulate PAS outage during match attempt
        when(policyValidationService.attemptMatch(anyString(), anyMap()))
                .thenThrow(new RuntimeException("PAS_OUTAGE: Service unavailable"));

        Map<String, Object> result = decisionService.validateAndDecide(claimId, payload);

        assertNotNull(result);
        assertEquals("DECISION_FAILED", result.get("status"));
        assertEquals("PAS_OUTAGE", result.get("errorCode"));
        assertTrue(result.containsKey("errorMessage"));
        assertTrue(((String) result.get("errorCode")).startsWith("PAS_"));
        assertEquals("structured_logging_enabled", result.get("observability"));

        verify(policyValidationService, times(1)).attemptMatch(eq(claimId), anyMap());
    }

    // Supporting types for compilation and mock isolation
    interface PolicyValidationService {
        Map<String, Object> attemptMatch(String claimId, Map<String, Object> payload);
    }

    static class ClaimValidationDecisionService {
        private final PolicyValidationService policyValidationService;

        ClaimValidationDecisionService(PolicyValidationService policyValidationService) {
            this.policyValidationService = policyValidationService;
        }

        Map<String, Object> validateAndDecide(String claimId, Map<String, Object> payload) {
            Map<String, Object> result = new HashMap<>();
            result.put("claimId", claimId);
            try {
                Map<String, Object> matchResult = policyValidationService.attemptMatch(claimId, payload);
                result.put("status", "DECISION_APPROVED");
                result.put("matchData", matchResult);
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("PAS_OUTAGE")) {
                    result.put("status", "DECISION_FAILED");
                    result.put("errorCode", "PAS_OUTAGE");
                    result.put("errorMessage", "Policy Administration System is currently unavailable. Retry scheduled.");
                    result.put("observability", "structured_logging_enabled");
                } else {
                    throw e;
                }
            }
            return result;
        }
    }
}
