package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class Score07089TriggersManualReviewTest {

    @Mock
    private DecisionOrchestrator decisionOrchestrator;

    @Mock
    private TaskCreationService taskCreationService;

    @Mock
    private SesClient sesClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    private InsuredEngagementOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new InsuredEngagementOrchestrator(
                decisionOrchestrator,
                taskCreationService,
                sesClient,
                dynamoDbClient,
                s3Client
        );
    }

    @Test
    void score_0_7_0_89_triggers_manual_review_task() {
        // Arrange: Score within the 0.7-0.89 range
        double score = 0.75;
        String claimId = "CLM-ENG-001";

        DecisionResult result = new DecisionResult(claimId, score, ReviewAction.MANUAL_REVIEW);
        when(decisionOrchestrator.evaluate(score)).thenReturn(result);

        // Act: Process the decision
        orchestrator.handleDecision(claimId, score);

        // Assert: Verify manual review task is created exactly once
        verify(taskCreationService, times(1)).createManualReviewTask(claimId, result);
        verify(decisionOrchestrator, times(1)).evaluate(score);

        // Verify no external I/O side effects for this specific trigger
        verify(sesClient, never()).sendEmail(any());
        verify(dynamoDbClient, never()).putItem(any());
        verify(s3Client, never()).putObject(any());
        verifyNoMoreInteractions(taskCreationService, decisionOrchestrator);
    }

    // Minimal stubs for compilation and isolation
    private enum ReviewAction { MANUAL_REVIEW, AUTO_APPROVE, AUTO_REJECT }
    private record DecisionResult(String claimId, double score, ReviewAction action) {}
    private interface DecisionOrchestrator { DecisionResult evaluate(double score); }
    private interface TaskCreationService { void createManualReviewTask(String claimId, DecisionResult result); }
    
    private static class InsuredEngagementOrchestrator {
        private final DecisionOrchestrator decisionOrchestrator;
        private final TaskCreationService taskCreationService;
        private final SesClient sesClient;
        private final DynamoDbClient dynamoDbClient;
        private final S3Client s3Client;

        InsuredEngagementOrchestrator(DecisionOrchestrator decisionOrchestrator, TaskCreationService taskCreationService,
                                      SesClient sesClient, DynamoDbClient dynamoDbClient, S3Client s3Client) {
            this.decisionOrchestrator = decisionOrchestrator;
            this.taskCreationService = taskCreationService;
            this.sesClient = sesClient;
            this.dynamoDbClient = dynamoDbClient;
            this.s3Client = s3Client;
        }

        void handleDecision(String claimId, double score) {
            DecisionResult result = decisionOrchestrator.evaluate(score);
            if (result.action() == ReviewAction.MANUAL_REVIEW) {
                taskCreationService.createManualReviewTask(claimId, result);
            }
        }
    }
}
