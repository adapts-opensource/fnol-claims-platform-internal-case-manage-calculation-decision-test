package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
public class IfDateDuringMoratoriumApplyBindingRestrictionRulesTest {

    @Mock
    private MoratoriumChecker moratoriumChecker;

    @Mock
    private BindingRestrictionApplier bindingRestrictionApplier;

    @InjectMocks
    private DecisionOrchestrator decisionOrchestrator;

    @Test
    void if_date_during_moratorium_apply_binding_restriction_rules() {
        // Arrange
        String insuredId = "INS-MOR-001";
        LocalDate decisionDate = LocalDate.of(2024, 8, 12);
        when(moratoriumChecker.isDuringMoratorium(insuredId, decisionDate)).thenReturn(true);

        // Act
        decisionOrchestrator.evaluateDecision(insuredId, decisionDate);

        // Assert
        verify(bindingRestrictionApplier, times(1)).applyRestrictionRules(insuredId, decisionDate);
        verifyNoMoreInteractions(bindingRestrictionApplier);
        verify(moratoriumChecker).isDuringMoratorium(insuredId, decisionDate);
    }
}
