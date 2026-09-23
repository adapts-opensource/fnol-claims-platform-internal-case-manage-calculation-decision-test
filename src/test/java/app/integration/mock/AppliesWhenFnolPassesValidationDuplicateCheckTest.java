package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Data Standardization:calculation:decision.
 * Validates that the decision engine proceeds when FNOL validation passes and duplicate check clears.
 * External I/O is mocked to comply with security, idempotency, and NFR constraints.
 */
@ExtendWith(MockitoExtension.class)
class AppliesWhenFnolPassesValidationDuplicateCheckTest {

    @Mock
    private FnolValidationService fnolValidationService;

    @Mock
    private DuplicateCheckService duplicateCheckService;

    @InjectMocks
    private ClaimDecisionEngine claimDecisionEngine;

    @BeforeEach
    void setUp() {
        // MockitoExtension auto-injects mocks; setup retained for lifecycle compliance
    }

    @Test
    void applies_when_fnol_passes_validation_duplicate_check() {
        // Arrange: Simulate FNOL passes validation & duplicate check returns false
        String claimId = "claim-std-001";
        String tenantId = "newco-insurance";

        when(fnolValidationService.validate(anyString())).thenReturn(true);
        when(duplicateCheckService.isDuplicate(anyString())).thenReturn(false);

        // Act: Execute decision logic
        boolean decisionProceeds = claimDecisionEngine.evaluate(claimId, tenantId);

        // Assert: Verify decision proceeds and external calls were made exactly once
        assertTrue(decisionProceeds, "Decision must proceed when FNOL validation passes and duplicate check clears");
        verify(fnolValidationService, times(1)).validate(anyString());
        verify(duplicateCheckService, times(1)).isDuplicate(anyString());
    }

    // Minimal service interfaces for mock injection (represent external/integration boundaries)
    interface FnolValidationService {
        boolean validate(String claimId);
    }

    interface DuplicateCheckService {
        boolean isDuplicate(String claimId);
    }

    // System Under Test (mocked dependencies injected via @InjectMocks)
    static class ClaimDecisionEngine {
        private final FnolValidationService fnolValidationService;
        private final DuplicateCheckService duplicateCheckService;

        ClaimDecisionEngine(FnolValidationService fnolValidationService, DuplicateCheckService duplicateCheckService) {
            this.fnolValidationService = fnolValidationService;
            this.duplicateCheckService = duplicateCheckService;
        }

        boolean evaluate(String claimId, String tenantId) {
            // Idempotency & tenant isolation context preserved in real impl
            boolean isValid = fnolValidationService.validate(claimId);
            boolean isDup = duplicateCheckService.isDuplicate(claimId);
            return isValid && !isDup;
        }
    }
}
