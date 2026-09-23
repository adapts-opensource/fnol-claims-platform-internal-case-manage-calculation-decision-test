package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RuleVersionMismatchTest {

    @Mock
    private RuleVersionChecker ruleVersionChecker;

    @Mock
    private StateTransitionGateway stateTransitionGateway;

    @Mock
    private StructuredLogger structuredLogger;

    private InsuredEngagementEngine insuredEngagementEngine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        insuredEngagementEngine = new InsuredEngagementEngine(ruleVersionChecker, stateTransitionGateway, structuredLogger);
    }

    @Test
    void rule_version_mismatch() {
        // Arrange
        String insuredId = "INS-10293";
        String targetState = "APPROVAL_IN_PROGRESS";
        String expectedRuleVersion = "2.1.0";
        String currentRuleVersion = "1.5.0";

        when(ruleVersionChecker.getAppliedVersion()).thenReturn(currentRuleVersion);
        when(stateTransitionGateway.validateRuleVersion(expectedRuleVersion, currentRuleVersion)).thenReturn(false);

        // Act & Assert
        RuleVersionMismatchException exception = assertThrows(RuleVersionMismatchException.class, () -> {
            insuredEngagementEngine.transitionState(insuredId, targetState, expectedRuleVersion);
        });

        assertEquals("Rule version mismatch: expected " + expectedRuleVersion + " but found " + currentRuleVersion, exception.getMessage());

        verify(structuredLogger).log(
            "state_transition",
            "rule_version_mismatch",
            insuredId,
            "expected=" + expectedRuleVersion + ",actual=" + currentRuleVersion
        );

        verify(stateTransitionGateway, never()).saveTransitionState(anyString(), anyString());
    }

    // Package-private stubs for compilation context
    interface RuleVersionChecker { String getAppliedVersion(); }
    interface StateTransitionGateway { boolean validateRuleVersion(String expected, String actual); void saveTransitionState(String insuredId, String state); }
    interface StructuredLogger { void log(String category, String eventType, String insuredId, String details); }
    class RuleVersionMismatchException extends RuntimeException {
        RuleVersionMismatchException(String message) { super(message); }
    }
    static class InsuredEngagementEngine {
        private final RuleVersionChecker checker;
        private final StateTransitionGateway gateway;
        private final StructuredLogger logger;
        InsuredEngagementEngine(RuleVersionChecker checker, StateTransitionGateway gateway, StructuredLogger logger) {
            this.checker = checker;
            this.gateway = gateway;
            this.logger = logger;
        }
        void transitionState(String insuredId, String targetState, String expectedRuleVersion) {
            String currentVersion = checker.getAppliedVersion();
            if (!gateway.validateRuleVersion(expectedRuleVersion, currentVersion)) {
                logger.log("state_transition", "rule_version_mismatch", insuredId, "expected=" + expectedRuleVersion + ",actual=" + currentVersion);
                throw new RuleVersionMismatchException("Rule version mismatch: expected " + expectedRuleVersion + " but found " + currentVersion);
            }
            gateway.saveTransitionState(insuredId, targetState);
        }
    }
}
