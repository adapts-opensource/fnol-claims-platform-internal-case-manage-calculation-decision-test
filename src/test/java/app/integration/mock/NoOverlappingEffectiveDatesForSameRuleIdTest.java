package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionRuleDateValidationTest {

    @Mock
    private RuleStateTransitionRepository ruleStateTransitionRepository;

    private InsuredEngagementStateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        stateTransitionService = new InsuredEngagementStateTransitionService(ruleStateTransitionRepository);
    }

    @Test
    @DisplayName("NoOverlappingEffectiveDatesForSameRuleId")
    void no_overlapping_effective_dates_for_same_rule_id() {
        String ruleId = "ENG-RULE-789";
        LocalDate proposedStart = LocalDate.of(2024, 11, 1);
        LocalDate proposedEnd = LocalDate.of(2024, 11, 30);

        // Mock external persistence: no existing overlapping rule found
        when(ruleStateTransitionRepository.findOverlappingActiveRules(
                eq(ruleId), eq(proposedStart), eq(proposedEnd)))
                .thenReturn(Optional.empty());

        // Assert: Validation should pass without throwing an exception
        assertDoesNotThrow(() -> stateTransitionService.validateEffectiveDateRange(
                ruleId, proposedStart, proposedEnd));

        // Verify: External query was executed exactly once
        verify(ruleStateTransitionRepository, times(1))
                .findOverlappingActiveRules(ruleId, proposedStart, proposedEnd);
    }
}
