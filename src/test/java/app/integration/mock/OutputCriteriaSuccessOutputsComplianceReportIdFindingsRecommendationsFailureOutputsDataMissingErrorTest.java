package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StateTransitionOutputCriteriaTest {

    @Mock
    private ComplianceStateTransitionEngine transitionEngine;

    private InsuredEngagementStateService stateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stateService = new InsuredEngagementStateService(transitionEngine);
    }

    @Test
    void output_criteria_success_outputs_compliance_report_id_findings_recommendations_failure_outputs_data_missing_error_retention_violation() {
        // Arrange: Success Path
        when(transitionEngine.evaluateState(anyString())).thenReturn(true);
        Map<String, Object> successOutput = stateService.transitionState("INS-1001");

        // Assert: Success outputs present, failure outputs absent
        assertNotNull(successOutput.get("compliance_report_id"));
        assertNotNull(successOutput.get("findings"));
        assertNotNull(successOutput.get("recommendations"));
        assertNull(successOutput.get("data_missing_error"));
        assertNull(successOutput.get("retention_violation"));

        // Arrange: Failure Path
        when(transitionEngine.evaluateState(anyString())).thenReturn(false);
        Map<String, Object> failureOutput = stateService.transitionState("INS-1002");

        // Assert: Failure outputs present, success outputs absent
        assertNull(failureOutput.get("compliance_report_id"));
        assertNull(failureOutput.get("findings"));
        assertNull(failureOutput.get("recommendations"));
        assertNotNull(failureOutput.get("data_missing_error"));
        assertNotNull(failureOutput.get("retention_violation"));
    }
}

// Minimal service and interface stubs for compilation context
interface ComplianceStateTransitionEngine {
    boolean evaluateState(String insuredId);
}

class InsuredEngagementStateService {
    private final ComplianceStateTransitionEngine engine;

    InsuredEngagementStateService(ComplianceStateTransitionEngine engine) {
        this.engine = engine;
    }

    Map<String, Object> transitionState(String insuredId) {
        Map<String, Object> result = new HashMap<>();
        if (engine.evaluateState(insuredId)) {
            result.put("compliance_report_id", "CR-" + insuredId);
            result.put("findings", "All data validated");
            result.put("recommendations", "Proceed to next state");
        } else {
            result.put("data_missing_error", "Required insured fields missing");
            result.put("retention_violation", "Data retention policy violation detected");
        }
        return result;
    }
}
