package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PurposePresentDuplicateCandidatesAllowReviewerDecisionAndTest {

    @Mock
    private StateTransitionOrchestrator orchestrator;

    @Mock
    private ClaimDataStoreService claimDataStore;

    @Mock
    private DocumentManagementService docManagement;

    private static final String TEST_CLAIM_ID = "claim-std-001";
    private static final String TEST_STATE_ID = "state-orch-pk-001";
    private static final String TABLE_NAME = "Claim Data Store_table";
    private static final String PARTITION_KEY = "pk";
    private static final String BUCKET_NAME = "Document Management-bucket";
    private static final String OBJECT_KEY_PATTERN = "Document Management/{entity_id}.json";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void purpose_present_duplicate_candidates_allow_reviewer_decision_and_execute_merge_or_continue_actions() {
        // Arrange: Mock duplicate candidates retrieval from DynamoDB
        List<Map<String, Object>> duplicateCandidates = List.of(
            Map.of("id", "candidate-alpha", "payload", Map.of("type", "FNOL", "status", "NEW")),
            Map.of("id", "candidate-beta", "payload", Map.of("type", "FNOL", "status", "NEW"))
        );
        when(claimDataStore.scanTable(TABLE_NAME, PARTITION_KEY, TEST_CLAIM_ID))
                .thenReturn(duplicateCandidates);

        // Act 1: Present duplicate candidates to reviewer
        Map<String, Object> presentationResponse = orchestrator.presentDuplicateCandidates(TEST_CLAIM_ID);
        assertNotNull(presentationResponse);
        assertTrue(presentationResponse.containsKey("candidates"));
        assertEquals(2, ((List<?>) presentationResponse.get("candidates")).size());

        // Arrange: Simulate reviewer decision
        String decisionType = "MERGE";
        Map<String, Object> reviewerPayload = Map.of(
            "decision", decisionType,
            "selectedCandidateId", "candidate-alpha",
            "actionTimestamp", "2023-11-15T14:30:00Z"
        );

        // Arrange: Mock state transition persistence to DynamoDB
        Map<String, Object> stateTransitionItem = Map.of(
            "id", TEST_STATE_ID,
            "payload", reviewerPayload
        );
        doNothing().when(claimDataStore).putItem(TABLE_NAME, PARTITION_KEY, stateTransitionItem);

        // Arrange: Mock document update in S3
        String resolvedObjectKey = String.format(OBJECT_KEY_PATTERN, TEST_CLAIM_ID);
        when(docManagement.putObject(BUCKET_NAME, resolvedObjectKey, reviewerPayload))
                .thenReturn("s3://" + BUCKET_NAME + "/" + resolvedObjectKey);

        // Act 2: Execute merge or continue action based on reviewer decision
        String executionResult = orchestrator.executeMergeOrContinueAction(TEST_STATE_ID, decisionType, reviewerPayload);

        // Assert: Verify orchestration flow and external I/O interactions
        assertEquals("EXECUTED", executionResult);
        verify(orchestrator).presentDuplicateCandidates(TEST_CLAIM_ID);
        verify(claimDataStore).putItem(TABLE_NAME, PARTITION_KEY, stateTransitionItem);
        verify(docManagement).putObject(BUCKET_NAME, resolvedObjectKey, reviewerPayload);
    }
}
