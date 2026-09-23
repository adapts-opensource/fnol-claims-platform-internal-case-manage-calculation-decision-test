package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseDecisionCalculationTest {

    @Mock
    private PolicyMatchRepository policyMatchRepository;

    @Mock
    private IntakeProcessingRepository intakeProcessingRepository;

    @Mock
    private DecisionCalculationEngine decisionCalculationEngine;

    @InjectMocks
    private CaseDecisionWorkflow caseDecisionWorkflow;

    private static final String INTAKE_ID = "intake-789";
    private static final String POLICY_MATCH_ID = "pm-456";
    private static final Map<String, Object> EXPECTED_DECISION_PAYLOAD = Map.of(
            "decisionId", "dec-001",
            "status", "APPROVED",
            "riskScore", 85,
            "calculatedAt", "2024-01-15T10:30:00Z"
    );

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and reset
    }

    @Test
    void applies_when_new_intake_processed_after_policy_match() {
        // Given: A policy match exists and the new intake has been successfully processed
        when(policyMatchRepository.findMatchByIntakeId(INTAKE_ID))
                .thenReturn(Optional.of(POLICY_MATCH_ID));
        when(intakeProcessingRepository.isIntakeProcessed(INTAKE_ID))
                .thenReturn(true);
        when(decisionCalculationEngine.calculateDecision(any(Map.class)))
                .thenReturn(EXPECTED_DECISION_PAYLOAD);

        // When: The decision workflow is triggered for the new intake
        Map<String, Object> result = caseDecisionWorkflow.processDecisionForNewIntake(INTAKE_ID);

        // Then: The decision calculation applies and returns the expected payload
        assertNotNull(result, "Decision result should not be null");
        assertEquals(EXPECTED_DECISION_PAYLOAD, result, "Decision payload should match expected");

        // Verify external I/O interactions occur in the correct sequence
        verify(policyMatchRepository, times(1)).findMatchByIntakeId(INTAKE_ID);
        verify(intakeProcessingRepository, times(1)).isIntakeProcessed(INTAKE_ID);
        verify(decisionCalculationEngine, times(1)).calculateDecision(any(Map.class));
        verifyNoMoreInteractions(policyMatchRepository, intakeProcessingRepository, decisionCalculationEngine);
    }
}
