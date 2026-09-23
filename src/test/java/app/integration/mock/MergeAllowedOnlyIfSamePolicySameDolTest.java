package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Data Standardization:state_transition:orchestration.
 * NFR Notes:
 * - Thread Safety: Test uses isolated mocks and avoids shared mutable state.
 * - Structured Logging: Service layer emits JSON logs; mocked clients verify contract I/O.
 * - Input Validation: Orchestration enforces policy/DoL/cause matching before merge.
 * - Security: PII is excluded from payload; TLS/IAM enforced at infra boundary.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient dynamoDbClient;
    @Mock
    private DocumentManagementClient s3Client;
    @Mock
    private StateTransitionValidator stateValidator;

    @InjectMocks
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    private Map<String, Object> basePayload;

    @BeforeEach
    void setUp() {
        // Initialize payload conforming to claim_data_standardization_state_transition_orch model
        basePayload = new HashMap<>();
        basePayload.put("id", UUID.randomUUID().toString());
        basePayload.put("policyId", "POL-9876");
        basePayload.put("dateOfLoss", "2024-08-12");
        basePayload.put("cause", "ACCIDENT");
        basePayload.put("status", "INITIATED");
        basePayload.put("region", "US-EAST-1");
    }

    @Test
    void merge_allowed_only_if_same_policy_same_dol_same_cause() {
        // Arrange: Prepare identical source and target payloads to satisfy merge conditions
        Map<String, Object> sourcePayload = new HashMap<>(basePayload);
        Map<String, Object> targetPayload = new HashMap<>(basePayload);

        // Mock validation to return true when policy, DoL, and cause match
        when(stateValidator.validateMergeEligibility(sourcePayload, targetPayload)).thenReturn(true);
        when(dynamoDbClient.putItem(eq("Claim Data Store_table"), anyMap())).thenReturn(Map.of("Item", Map.of("id", sourcePayload.get("id"))));
        when(s3Client.putObject(eq("Document Management-bucket"), anyString(), any(byte[].class)))
                .thenReturn("s3://Document Management-bucket/merge-result.json");

        // Act: Execute orchestration merge flow
        String mergeResultId = orchestrationService.processStateTransitionMerge(sourcePayload, targetPayload);

        // Assert: Verify merge succeeds and infra contracts are invoked correctly
        assertNotNull(mergeResultId, "Merge should return a valid result ID when conditions match");
        verify(stateValidator, times(1)).validateMergeEligibility(sourcePayload, targetPayload);
        
        ArgumentCaptor<Map<String, Object>> capturedDynamoItem = ArgumentCaptor.forClass(Map.class);
        verify(dynamoDbClient, times(1)).putItem(eq("Claim Data Store_table"), capturedDynamoItem.capture());
        assertTrue(capturedDynamoItem.getValue().containsKey("id"), "DynamoDB item must contain entity ID");
        
        verify(s3Client, times(1)).putObject(eq("Document Management-bucket"), anyString(), any(byte[].class));
    }
}
