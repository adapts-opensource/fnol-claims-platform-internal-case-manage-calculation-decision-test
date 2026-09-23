package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
class MultipleMatchesTriggerReviewTaskTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @InjectMocks
    private ClaimDataTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks automatically
    }

    @Test
    void multiple_matches_trigger_review_task() {
        // Arrange
        String claimId = "claim-789";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("payload", Map.of(
                "entity_type", "claim_data_standardization_calculation_transform",
                "matches", List.of("match_alpha", "match_beta", "match_gamma"),
                "confidence_scores", List.of(0.92, 0.88, 0.85),
                "status", "PENDING_STANDARDIZATION"
        ));

        String auditBucket = "AuditDiaryStore-bucket";
        String auditKeyPattern = "AuditDiaryStore/" + claimId + ".json";

        when(auditDiaryStoreService.writeAuditDiary(eq(auditBucket), eq(auditKeyPattern), anyString()))
                .thenReturn("s3://" + auditBucket + "/" + auditKeyPattern);

        when(rulesEngineDecisionService.evaluate(inputPayload))
                .thenReturn(Map.of("decision", "MULTIPLE_MATCHES", "action", "TRIGGER_REVIEW"));

        doNothing().when(workflowTaskRouterService).createReviewTask(eq(claimId), anyMap());

        // Act
        Map<String, Object> result = transformationService.transformClaimData(claimId, inputPayload);

        // Assert - Transformation Result
        assertNotNull(result, "Transformation result should not be null");
        assertEquals("MULTIPLE_MATCHES", result.get("decision"), "Decision should be MULTIPLE_MATCHES");
        assertEquals("TRIGGER_REVIEW", result.get("action"), "Action should be TRIGGER_REVIEW");
        assertTrue(((List<?>) result.get("matches")).size() > 1, "Should contain multiple matches");

        // Assert - S3 Audit Diary
        verify(auditDiaryStoreService, times(1))
                .writeAuditDiary(eq(auditBucket), eq(auditKeyPattern), anyString());

        // Assert - DynamoDB Workflow Task Router
        verify(workflowTaskRouterService, times(1))
                .createReviewTask(eq(claimId), anyMap());

        // Assert - Rules Engine
        verify(rulesEngineDecisionService, times(1))
                .evaluate(inputPayload);
    }
}
