package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DolValidationCorrectlyIdentifiesOutOfPeriodCasesTest {

    @Mock
    private ClaimStateTransitionService claimStateTransitionService;

    @Mock
    private DolPeriodValidator dolPeriodValidator;

    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private LocalDate outOfPeriodDol;

    @BeforeEach
    void setUp() {
        policyStartDate = LocalDate.of(2023, 1, 1);
        policyEndDate = LocalDate.of(2023, 12, 31);
        outOfPeriodDol = LocalDate.of(2022, 6, 15);
    }

    @Test
    void dol_validation_correctly_identifies_out_of_period_cases() {
        // Arrange: Mock validator to indicate DOL is outside the policy window
        when(dolPeriodValidator.isWithinPolicyPeriod(outOfPeriodDol, policyStartDate, policyEndDate))
                .thenReturn(false);

        // Act & Assert: Verify that attempting a state transition throws the expected validation exception
        assertThrows(InvalidPeriodException.class, () -> {
            claimStateTransitionService.processStateTransition("CLAIM-001", "INITIATED", outOfPeriodDol, policyStartDate, policyEndDate);
        });

        // Verify validation logic was explicitly invoked before any state mutation or external I/O
        verify(dolPeriodValidator, times(1)).isWithinPolicyPeriod(outOfPeriodDol, policyStartDate, policyEndDate);
        verifyNoInteractions(claimStateTransitionService);
    }

    // Minimal interfaces for compilation context; in production these reside in the domain layer
    public interface DolPeriodValidator {
        boolean isWithinPolicyPeriod(LocalDate dol, LocalDate startDate, LocalDate endDate);
    }

    public interface ClaimStateTransitionService {
        void processStateTransition(String claimId, String fromState, LocalDate dol, LocalDate startDate, LocalDate endDate);
    }

    public class InvalidPeriodException extends RuntimeException {
        public InvalidPeriodException(String message) {
            super(message);
        }
    }
}
