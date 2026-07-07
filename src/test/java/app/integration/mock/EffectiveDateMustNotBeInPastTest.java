package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock tests for Claim Data Standardization validation decision logic.
 * Verifies behavior of transformation and validation rules using mocked infrastructure services.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationService standardizationService;

    @Test
    @DisplayName("EffectiveDateMustNotBeInPast")
    void effective_date_must_not_be_in_past() {
        // Arrange
        String claimId = "CLM-STD-001";
        LocalDate pastDate = LocalDate.now().minusYears(2);
        String pastDateStr = pastDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("effective_date", pastDateStr);
        payload.put("claim_type", "AUTO");

        // Mock infrastructure responses
        when(policyValidationService.validatePolicy(anyString(), anyString()))
                .thenReturn(Map.of("status", "ACTIVE"));
        
        when(rulesEngineService.evaluateRules(anyString(), anyMap()))
                .thenReturn(Map.of("rule_result", "PENDING"));

        // Act
        StandardizationResult result = standardizationService.processClaim(payload);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertFalse(result.isValid(), "Validation should fail for past effective date");
        assertEquals(ValidationDecision.REJECTED, result.getDecision(), "Decision should be REJECTED");
        assertTrue(result.getErrors().containsKey("effective_date"), "Error should be present for effective_date field");
        
        // Verify no downstream processing occurs for invalid data
        verify(rulesEngineService, never()).executeDecision(anyString(), anyMap());
        verify(policyValidationService, never()).storeValidationResult(anyString(), anyMap());
    }

    /**
     * Simplified result model for demonstration purposes.
     * In a real project, this would be imported from the domain model.
     */
    static class StandardizationResult {
        private boolean valid;
        private ValidationDecision decision;
        private Map<String, String> errors;

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public ValidationDecision getDecision() { return decision; }
        public void setDecision(ValidationDecision decision) { this.decision = decision; }
        
        public Map<String, String> getErrors() { return errors; }
        public void setErrors(Map<String, String> errors) { this.errors = errors; }
    }

    /**
     * Mock service interface representing the Claim Data Standardization logic.
     * In a real project, this would be the actual service implementation.
     */
    static class ClaimDataStandardizationService {
        
        private PolicyValidationService policyValidationService;
        private RulesEngineService rulesEngineService;

        public void setPolicyValidationService(PolicyValidationService policyValidationService) {
            this.policyValidationService = policyValidationService;
        }

        public void setRulesEngineService(RulesEngineService rulesEngineService) {
            this.rulesEngineService = rulesEngineService;
        }

        public StandardizationResult processClaim(Map<String, Object> payload) {
            String effectiveDateStr = (String) payload.get("effective_date");
            if (effectiveDateStr == null || effectiveDateStr.isEmpty()) {
                StandardizationResult result = new StandardizationResult();
                result.setValid(false);
                result.setDecision(ValidationDecision.REJECTED);
                result.setErrors(Map.of("effective_date", "Effective date is required"));
                return result;
            }

            try {
                LocalDate effectiveDate = LocalDate.parse(effectiveDateStr);
                if (effectiveDate.isBefore(LocalDate.now())) {
                    StandardizationResult result = new StandardizationResult();
                    result.setValid(false);
                    result.setDecision(ValidationDecision.REJECTED);
                    result.setErrors(Map.of("effective_date", "Effective date must not be in the past"));
                    return result;
                }
            } catch (Exception e) {
                StandardizationResult result = new StandardizationResult();
                result.setValid(false);
                result.setDecision(ValidationDecision.REJECTED);
                result.setErrors(Map.of("effective_date", "Invalid date format"));
                return result;
            }

            // Proceed with validation if date is valid
            // ...
            StandardizationResult result = new StandardizationResult();
            result.setValid(true);
            result.setDecision(ValidationDecision.PASSED);
            result.setErrors(Map.of());
            return result;
        }
    }
}
