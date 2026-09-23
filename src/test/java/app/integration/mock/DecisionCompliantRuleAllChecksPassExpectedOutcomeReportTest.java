package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionStateTransitionMockTest {

    @Mock
    private DecisionValidator decisionValidator;

    @Mock
    private ReportPublisher reportPublisher;

    private DecisionStateTransitionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DecisionStateTransitionHandler(decisionValidator, reportPublisher);
    }

    @Test
    void decision_compliant_rule_all_checks_pass_expected_outcome_report_generated() {
        // Given
        String decision = "Compliant";
        String rule = "All checks pass";

        when(decisionValidator.validate(decision, rule)).thenReturn(true);

        // When
        boolean reportGenerated = handler.executeTransition(decision, rule);

        // Then
        assertTrue(reportGenerated, "Expected report to be generated when decision is Compliant and all checks pass");
        verify(reportPublisher, times(1)).publish(anyString());
        verify(decisionValidator, times(1)).validate(decision, rule);
    }

    // Supporting interfaces/classes for isolated compilation
    interface DecisionValidator {
        boolean validate(String decision, String rule);
    }

    interface ReportPublisher {
        void publish(String reportContent);
    }

    static class DecisionStateTransitionHandler {
        private final DecisionValidator validator;
        private final ReportPublisher publisher;

        DecisionStateTransitionHandler(DecisionValidator validator, ReportPublisher publisher) {
            this.validator = validator;
            this.publisher = publisher;
        }

        boolean executeTransition(String decision, String rule) {
            if (validator.validate(decision, rule)) {
                publisher.publish("Compliant_Report_Generated");
                return true;
            }
            return false;
        }
    }
}
