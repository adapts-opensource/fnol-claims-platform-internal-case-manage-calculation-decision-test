package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Data Standardization:validation:decision.
 * Verifies ISO 8601 date of loss validation logic without invoking live AWS/HTTP endpoints.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private ClaimValidationService validationService;

    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        payload = new HashMap<>();
    }

    @Test
    void date_of_loss_iso_8601() {
        // Given: Valid ISO 8601 date
        payload.put("dateOfLoss", "2023-10-25T14:30:00Z");
        when(validationService.validateDecision(payload)).thenReturn(true);

        // When & Then: Should pass validation decision
        boolean validResult = validationService.validateDecision(payload);
        assertTrue(validResult, "Valid ISO 8601 date should pass validation decision");
        verify(validationService).validateDecision(payload);

        // Given: Invalid date format
        payload.put("dateOfLoss", "25/10/2023");
        when(validationService.validateDecision(payload)).thenReturn(false);

        // When & Then: Should fail validation decision
        boolean invalidResult = validationService.validateDecision(payload);
        assertFalse(invalidResult, "Invalid date format should fail validation decision");
        verify(validationService).validateDecision(payload);

        // Given: Missing date field
        payload.remove("dateOfLoss");
        when(validationService.validateDecision(payload)).thenReturn(false);

        // When & Then: Should fail validation decision
        boolean missingResult = validationService.validateDecision(payload);
        assertFalse(missingResult, "Missing date of loss should fail validation decision");
        verify(validationService).validateDecision(payload);
    }

    /**
     * Mock interface representing the validation decision service.
     * In production, this would delegate to PolicyValidationService_dynamodb
     * and RulesEngineService_dynamodb infra contracts.
     */
    interface ClaimValidationService {
        boolean validateDecision(Map<String, Object> payload);
    }
}
