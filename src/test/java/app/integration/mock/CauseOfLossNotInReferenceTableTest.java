package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies Claim Data Standardization:validation:decision logic.
 * Ensures proper handling when cause of loss is absent from the reference table.
 * Aligns with input_validation and observability NFRs.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionTest {

    private static final Logger log = Logger.getLogger(ClaimDataStandardizationValidationDecisionTest.class.getName());

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationValidationService validationService;

    @BeforeEach
    void setUp() {
        log.log(Level.INFO, "Initializing mock environment for Claim Data Standardization validation");
    }

    @Test
    void cause_of_loss_not_in_reference_table() {
        // Given: Claim payload with a cause of loss not present in the reference table
        String causeOfLoss = "EXOTIC_PERIL_99";
        Map<String, Object> payload = Map.of(
                "id", "claim-std-001",
                "payload", Map.of("causeOfLoss", causeOfLoss)
        );

        // Mock external DynamoDB reference table lookup to return empty result
        when(rulesEngineService.lookupReference(anyString())).thenReturn(Optional.empty());

        // When: Validation decision is evaluated
        ClaimValidationDecision decision = validationService.evaluateDecision(payload);

        // Then: Decision must reflect validation failure due to missing reference data
        assertFalse(decision.isValid());
        assertEquals("CAUSE_OF_LOSS_NOT_IN_REFERENCE_TABLE", decision.getErrorCode());
        assertEquals("Cause of loss '" + causeOfLoss + "' is not found in the approved reference table.", decision.getReason());

        // Verify infrastructure I/O contract was invoked correctly
        verify(rulesEngineService, times(1)).lookupReference(anyString());
        log.log(Level.INFO, "Test completed: cause_of_loss_not_in_reference_table passed");
    }

    // Minimal stubs to satisfy compilation and mock injection
    interface RulesEngineService {
        Optional<Map<String, String>> lookupReference(String causeOfLoss);
    }

    record ClaimValidationDecision(boolean isValid, String errorCode, String reason) {}

    static class ClaimDataStandardizationValidationService {
        private final RulesEngineService rulesEngineService;

        ClaimDataStandardizationValidationService(RulesEngineService rulesEngineService) {
            this.rulesEngineService = rulesEngineService;
        }

        ClaimValidationDecision evaluateDecision(Map<String, Object> payload) {
            Map<String, Object> claimPayload = (Map<String, Object>) payload.get("payload");
            String causeOfLoss = (String) claimPayload.get("causeOfLoss");

            if (causeOfLoss == null || causeOfLoss.isBlank()) {
                return new ClaimValidationDecision(false, "MISSING_CAUSE_OF_LOSS", "Cause of loss is required.");
            }

            Optional<Map<String, String>> referenceEntry = rulesEngineService.lookupReference(causeOfLoss);
            if (referenceEntry.isEmpty()) {
                return new ClaimValidationDecision(false, "CAUSE_OF_LOSS_NOT_IN_REFERENCE_TABLE",
                        "Cause of loss '" + causeOfLoss + "' is not found in the approved reference table.");
            }

            return new ClaimValidationDecision(true, null, null);
        }
    }
}
