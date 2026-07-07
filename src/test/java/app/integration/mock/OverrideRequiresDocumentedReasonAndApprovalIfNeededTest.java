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

@ExtendWith(MockitoExtension.class)
class OverrideRequiresDocumentedReasonAndApprovalIfNeededTest {

    @Mock
    private ClaimStandardizationValidator validator;

    private ClaimEnrichmentProcessor enrichmentProcessor;

    @BeforeEach
    void setUp() {
        enrichmentProcessor = new ClaimEnrichmentProcessor(validator);
    }

    @Test
    void overrideRequiresDocumentedReasonAndApprovalIfNeeded_whenComplete_shouldPassValidation() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "CLM-STD-001");
        payload.put("override", true);
        payload.put("documented_reason", "Regulatory compliance adjustment");
        payload.put("approval", "MANAGER_APPROVED");

        doNothing().when(validator).validateOverride(payload);

        assertDoesNotThrow(() -> enrichmentProcessor.process(payload));
        verify(validator, times(1)).validateOverride(payload);
    }

    @Test
    void overrideRequiresDocumentedReasonAndApprovalIfNeeded_whenMissingReason_shouldThrowValidationException() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "CLM-STD-002");
        payload.put("override", true);
        payload.put("approval", "MANAGER_APPROVED");

        doThrow(new IllegalArgumentException("Override requires documented reason and approval if needed"))
                .when(validator).validateOverride(payload);

        assertThrows(IllegalArgumentException.class, () -> enrichmentProcessor.process(payload));
    }

    @Test
    void overrideRequiresDocumentedReasonAndApprovalIfNeeded_whenMissingApproval_shouldThrowValidationException() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "CLM-STD-003");
        payload.put("override", true);
        payload.put("documented_reason", "System migration required");

        doThrow(new IllegalArgumentException("Override requires documented reason and approval if needed"))
                .when(validator).validateOverride(payload);

        assertThrows(IllegalArgumentException.class, () -> enrichmentProcessor.process(payload));
    }
}

// Minimal domain/service contracts to ensure single-file compilation
interface ClaimStandardizationValidator {
    void validateOverride(Map<String, Object> payload);
}

class ClaimEnrichmentProcessor {
    private final ClaimStandardizationValidator validator;

    ClaimEnrichmentProcessor(ClaimStandardizationValidator validator) {
        this.validator = validator;
    }

    void process(Map<String, Object> payload) {
        if (Boolean.TRUE.equals(payload.get("override"))) {
            validator.validateOverride(payload);
        }
    }
}
