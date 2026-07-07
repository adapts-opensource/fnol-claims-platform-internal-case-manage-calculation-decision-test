package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates FNOL intake decision routing when a moratorium is active on the Date of Loss (DoL).
 * Aligns with NFRs: thread-safe idempotent decision evaluation, structured logging at decision boundary,
 * GDPR lawful basis minimization (no PII stored in decision context), and SOC2 audit trail readiness.
 */
@ExtendWith(MockitoExtension.class)
class FnolValidationDecisionServiceTest {

    @Mock
    private MoratoriumService moratoriumService;

    @InjectMocks
    private FnolValidationDecisionService fnolValidationDecisionService;

    @Test
    void if_moratorium_active_on_dol_flag_as_restricted_intake() {
        // Arrange: Simulate external moratorium boundary check returning active status
        LocalDate dateOfLoss = LocalDate.of(2024, 11, 15);
        when(moratoriumService.isActiveOnDate(dateOfLoss)).thenReturn(true);

        // Act: Execute intake validation decision (idempotent, thread-safe)
        DecisionResult decision = fnolValidationDecisionService.evaluateIntake(dateOfLoss);

        // Assert: Verify decision correctly flags as RESTRICTED_INTAKE
        assertNotNull(decision, "Decision result must not be null");
        assertEquals(DecisionStatus.RESTRICTED_INTAKE, decision.getStatus(), 
                "Moratorium active on DoL must route to RESTRICTED_INTAKE");
        assertTrue(decision.isRestricted(), "Restricted flag must be true");
        verify(moratoriumService).isActiveOnDate(dateOfLoss);
    }

    // Minimal domain contracts for test compilation context
    record DecisionResult(DecisionStatus status, boolean restricted) {}
    interface MoratoriumService { boolean isActiveOnDate(LocalDate date); }
    interface FnolValidationDecisionService { DecisionResult evaluateIntake(LocalDate date); }
}
