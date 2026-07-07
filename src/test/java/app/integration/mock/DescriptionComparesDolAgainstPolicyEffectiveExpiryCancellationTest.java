package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MultiChannelFnolStateTransitionCalculationMockTest {

    @Mock
    private PolicyDateService policyDateService;

    @Mock
    private RestrictionChecker restrictionChecker;

    private StateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new StateTransitionCalculator(policyDateService, restrictionChecker);
    }

    @Test
    void description_compares_dol_against_policy_effective_expiry_cancellation_reinstatement_rewrite_dates_and_checks_for_active_moratoriums_or_catastrophe_restrictions() {
        // Arrange
        String policyId = "POL-789";
        LocalDate dol = LocalDate.of(2023, 6, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expiryDate = LocalDate.of(2023, 12, 31);
        LocalDate cancellationDate = null;
        LocalDate reinstatementDate = null;
        LocalDate rewriteDate = null;

        when(policyDateService.getEffectiveDate(policyId)).thenReturn(effectiveDate);
        when(policyDateService.getExpiryDate(policyId)).thenReturn(expiryDate);
        when(policyDateService.getCancellationDate(policyId)).thenReturn(cancellationDate);
        when(policyDateService.getReinstatementDate(policyId)).thenReturn(reinstatementDate);
        when(policyDateService.getRewriteDate(policyId)).thenReturn(rewriteDate);

        when(restrictionChecker.hasActiveMoratorium(policyId)).thenReturn(false);
        when(restrictionChecker.hasCatastropheRestriction(policyId)).thenReturn(false);

        // Act
        String result = calculator.calculateStateTransition(policyId, dol);

        // Assert
        assertEquals("ACTIVE", result, "DoL within valid policy window with no restrictions should yield ACTIVE");
        verify(policyDateService).getEffectiveDate(policyId);
        verify(policyDateService).getExpiryDate(policyId);
        verify(policyDateService).getCancellationDate(policyId);
        verify(policyDateService).getReinstatementDate(policyId);
        verify(policyDateService).getRewriteDate(policyId);
        verify(restrictionChecker).hasActiveMoratorium(policyId);
        verify(restrictionChecker).hasCatastropheRestriction(policyId);
    }
}

// Supporting interfaces for test isolation and mock verification
interface PolicyDateService {
    LocalDate getEffectiveDate(String policyId);
    LocalDate getExpiryDate(String policyId);
    LocalDate getCancellationDate(String policyId);
    LocalDate getReinstatementDate(String policyId);
    LocalDate getRewriteDate(String policyId);
}

interface RestrictionChecker {
    boolean hasActiveMoratorium(String policyId);
    boolean hasCatastropheRestriction(String policyId);
}

class StateTransitionCalculator {
    private final PolicyDateService policyDateService;
    private final RestrictionChecker restrictionChecker;

    StateTransitionCalculator(PolicyDateService policyDateService, RestrictionChecker restrictionChecker) {
        this.policyDateService = policyDateService;
        this.restrictionChecker = restrictionChecker;
    }

    String calculateStateTransition(String policyId, LocalDate dol) {
        LocalDate effective = policyDateService.getEffectiveDate(policyId);
        LocalDate expiry = policyDateService.getExpiryDate(policyId);
        LocalDate cancellation = policyDateService.getCancellationDate(policyId);
        LocalDate reinstatement = policyDateService.getReinstatementDate(policyId);
        LocalDate rewrite = policyDateService.getRewriteDate(policyId);

        if (cancellation != null && !dol.isBefore(cancellation)) {
            return "CANCELLED";
        }
        if (dol.isBefore(effective) || (expiry != null && !dol.isBefore(expiry))) {
            return "OUT_OF_SCOPE";
        }
        if (restrictionChecker.hasActiveMoratorium(policyId) || restrictionChecker.hasCatastropheRestriction(policyId)) {
            return "RESTRICTED";
        }
        return "ACTIVE";
    }
}
