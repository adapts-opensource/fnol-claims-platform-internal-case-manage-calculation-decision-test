package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/**
 * Integration mock test for Claim Data Standardization:decision:transformation.
 * Validates that ambiguous matches in claim payload correctly trigger a review task.
 */
@ExtendWith(MockitoExtension.class)
public class AmbiguousMatchesTriggerReviewTaskTest {

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @InjectMocks
    private ClaimDataDecisionTransformationService decisionTransformationService;

    private Map<String, Object> ambiguousMatchPayload;

    @BeforeEach
    void setUp() {
        ambiguousMatchPayload = new HashMap<>();
        ambiguousMatchPayload.put("id", "CLM-AMB-001");
        ambiguousMatchPayload.put("claimant_id", "CLNT-8842");
        ambiguousMatchPayload.put("ambiguous_matches", List.of("POLICY_MATCH_A", "POLICY_MATCH_B"));
        ambiguousMatchPayload.put("standardization_status", "COMPLETED");
        ambiguousMatchPayload.put("risk_score", 0.85);
    }

    @Test
    void ambiguous_matches_trigger_review_task() {
        // Arrange: Mock external decision service to return ambiguous match verdict
        when(rulesEngineDecisionService.evaluate(anyMap())).thenReturn(Map.of("verdict", "AMBIGUOUS"));

        // Act: Execute transformation logic
        Map<String, Object> result = decisionTransformationService.transformDecisionPayload(ambiguousMatchPayload);

        // Assert: Verify transformation output and external task routing
        assertNotNull(result);
        assertEquals("COMPLETED", result.get("standardization_status"));
        assertTrue((Boolean) result.get("requires_review"));

        // Verify external I/O mock: review task created with correct claim ID and reason
        verify(workflowTaskRouter, times(1))
            .createReviewTask(eq("CLM-AMB-001"), eq("AMBIGUOUS_MATCHES"), eq("HIGH"));

        // Verify no unintended calls to other infra contracts
        verifyNoMoreInteractions(workflowTaskRouter, rulesEngineDecisionService);
    }
}
