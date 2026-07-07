package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = Map.of(
            "id", "claim-12345",
            "causeOfLoss", "FIRE"
        );
    }

    @Test
    void cause_of_loss_must_map_to_supported_reference_table() {
        // Arrange: Mock reference table to return supported cause of loss codes
        List<String> supportedCodes = List.of("FIRE", "FLOOD", "THEFT", "ACCIDENT");
        when(rulesEngineService.getItemPayload(anyString(), anyString()))
            .thenReturn(Map.of("supportedCauseOfLossCodes", supportedCodes));

        // Act: Simulate validation decision logic
        Map<String, Object> result = evaluateValidationDecision(claimPayload);

        // Assert: Valid cause of loss should pass validation
        assertTrue((Boolean) result.get("isValid"), "Claim should be valid when causeOfLoss maps to supported reference");
        assertEquals("FIRE", result.get("causeOfLoss"));
        assertEquals("PASS", result.get("decision"));

        // Arrange: Unsupported cause of loss
        claimPayload = Map.of("id", "claim-67890", "causeOfLoss", "UNKNOWN_EVENT");
        when(rulesEngineService.getItemPayload(anyString(), anyString()))
            .thenReturn(Map.of("supportedCauseOfLossCodes", supportedCodes));

        // Act
        result = evaluateValidationDecision(claimPayload);

        // Assert
        assertFalse((Boolean) result.get("isValid"), "Claim should be invalid when causeOfLoss does not map to supported reference");
        assertEquals("UNKNOWN_EVENT", result.get("causeOfLoss"));
        assertEquals("REJECT", result.get("decision"));
        assertNotNull(result.get("validationError"), "Error message must be present for unsupported mapping");
    }

    private Map<String, Object> evaluateValidationDecision(Map<String, Object> payload) {
        Map<String, Object> referenceTable = rulesEngineService.getItemPayload("pk", "causeOfLossRef");
        @SuppressWarnings("unchecked")
        List<String> supportedCodes = (List<String>) referenceTable.get("supportedCauseOfLossCodes");
        String causeOfLoss = (String) payload.get("causeOfLoss");
        boolean isValid = supportedCodes.contains(causeOfLoss);

        return Map.of(
            "isValid", isValid,
            "causeOfLoss", causeOfLoss,
            "decision", isValid ? "PASS" : "REJECT",
            "validationError", isValid ? null : "CauseOfLossMustMapToSupportedReference"
        );
    }

    interface RulesEngineService {
        Map<String, Object> getItemPayload(String partitionKey, String sortKey);
    }

    interface DocumentStoreService {
        String getObjectUri(String bucketName, String objectKeyPattern);
    }

    interface PolicyValidationService {
        Map<String, Object> getItemPayload(String partitionKey, String sortKey);
    }
}
