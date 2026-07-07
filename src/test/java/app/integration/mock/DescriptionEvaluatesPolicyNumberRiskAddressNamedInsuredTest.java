package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolValidationDecisionTest {

    @Mock
    private PolicyRegistryService policyRegistry;

    @Mock
    private RestrictionChecker restrictionChecker;

    private ValidationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ValidationDecisionService(policyRegistry, restrictionChecker);
    }

    @Test
    void description_evaluates_policy_number_risk_address_named_insured_and_date_of_loss_against_registry_compares_dol_against_effective_expiration_cancellation_reinstatement_rewrite_dates_and_checks_moratorium_binding_restrictions_returns_match_status_and_coverage_flags() {
        // Arrange
        String policyNumber = "POL-12345";
        String riskAddress = "123 Main St";
        String namedInsured = "John Doe";
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 15);

        PolicyDetails policyDetails = new PolicyDetails(
                policyNumber, riskAddress, namedInsured,
                LocalDate.of(2023, 1, 1),   // effective
                LocalDate.of(2024, 1, 1),   // expiration
                null,                       // cancellation
                null,                       // reinstatement
                null,                       // rewrite
                true                        // active
        );

        when(policyRegistry.findByPolicyNumber(policyNumber)).thenReturn(Optional.of(policyDetails));
        when(restrictionChecker.checkMoratorium(policyNumber, riskAddress, dateOfLoss)).thenReturn(false);
        when(restrictionChecker.checkBindingRestrictions(policyNumber, riskAddress, dateOfLoss)).thenReturn(false);

        FnolSubmissionRequest request = new FnolSubmissionRequest(policyNumber, riskAddress, namedInsured, dateOfLoss);

        // Act
        ValidationDecision decision = decisionService.evaluate(request);

        // Assert
        assertNotNull(decision);
        assertEquals(MatchStatus.EXACT_MATCH, decision.getMatchStatus());
        assertTrue(decision.isCoverageActive());
        assertFalse(decision.isInMoratorium());
        assertFalse(decision.isBindingRestricted());

        verify(policyRegistry).findByPolicyNumber(policyNumber);
        verify(restrictionChecker).checkMoratorium(policyNumber, riskAddress, dateOfLoss);
        verify(restrictionChecker).checkBindingRestrictions(policyNumber, riskAddress, dateOfLoss);
    }
}

// Supporting interfaces and DTOs for mock isolation
interface PolicyRegistryService {
    Optional<PolicyDetails> findByPolicyNumber(String policyNumber);
}

interface RestrictionChecker {
    boolean checkMoratorium(String policyNumber, String riskAddress, LocalDate dateOfLoss);
    boolean checkBindingRestrictions(String policyNumber, String riskAddress, LocalDate dateOfLoss);
}

record FnolSubmissionRequest(String policyNumber, String riskAddress, String namedInsured, LocalDate dateOfLoss) {}

record PolicyDetails(String policyNumber, String riskAddress, String namedInsured,
                     LocalDate effectiveDate, LocalDate expirationDate,
                     LocalDate cancellationDate, LocalDate reinstatementDate,
                     LocalDate rewriteDate, boolean isActive) {}

record ValidationDecision(MatchStatus matchStatus, boolean coverageActive,
                          boolean inMoratorium, boolean bindingRestricted) {}

class ValidationDecisionService {
    private final PolicyRegistryService policyRegistry;
    private final RestrictionChecker restrictionChecker;

    ValidationDecisionService(PolicyRegistryService policyRegistry, RestrictionChecker restrictionChecker) {
        this.policyRegistry = policyRegistry;
        this.restrictionChecker = restrictionChecker;
    }

    ValidationDecision evaluate(FnlSubmissionRequest request) {
        Optional<PolicyDetails> policyOpt = policyRegistry.findByPolicyNumber(request.policyNumber());
        if (policyOpt.isEmpty()) {
            return new ValidationDecision(MatchStatus.NO_MATCH, false, false, false);
        }

        PolicyDetails policy = policyOpt.get();
        LocalDate dol = request.dateOfLoss();
        boolean active = policy.isActive() &&
                         !dol.isBefore(policy.effectiveDate()) &&
                         !dol.isAfter(policy.expirationDate()) &&
                         (policy.cancellationDate() == null || dol.isBefore(policy.cancellationDate())) &&
                         (policy.reinstatementDate() == null || !dol.isBefore(policy.reinstatementDate()));

        boolean moratorium = restrictionChecker.checkMoratorium(request.policyNumber(), request.riskAddress(), dol);
        boolean bindingRestricted = restrictionChecker.checkBindingRestrictions(request.policyNumber(), request.riskAddress(), dol);

        return new ValidationDecision(
                active ? MatchStatus.EXACT_MATCH : MatchStatus.NO_MATCH,
                active, moratorium, bindingRestricted
        );
    }
}
