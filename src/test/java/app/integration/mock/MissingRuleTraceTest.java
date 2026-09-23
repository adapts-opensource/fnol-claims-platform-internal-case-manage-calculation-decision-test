package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionValidationDecisionTest {

    @Mock
    private FnolDecisionEngine decisionEngine;

    @Mock
    private ClaimValidationService validationService;

    @BeforeEach
    void setUp() {
        // Reset mocks between tests to ensure isolation
        reset(decisionEngine, validationService);
    }

    @Test
    void missing_rule_trace() {
        // Arrange
        // Simulate a decision outcome where the rule trace is null/missing.
        // This scenario verifies that the system enforces audit requirements (SOC2/GDPR)
        // by rejecting decisions that lack traceability.
        DecisionResult decisionResult = DecisionResult.builder()
                .claimId("claim_123")
                .policyId("policy_456")
                .ruleTrace(null)
                .decisionStatus(DecisionStatus.PENDING)
                .build();

        when(decisionEngine.evaluate(any())).thenReturn(decisionResult);

        // Act & Assert
        // Verify that the validation service detects the missing rule trace
        // and throws a validation exception to prevent non-compliant claims.
        assertThrows(ValidationException.class, 
            () -> validationService.validateDecision(decisionResult),
            "Expected ValidationException when rule trace is missing"
        );
        
        // Verify decision engine was called
        verify(decisionEngine, times(1)).evaluate(any());
    }
}
