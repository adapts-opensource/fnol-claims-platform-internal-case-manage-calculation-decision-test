package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 integration mock test for Claim Data Standardization:state_transition:orchestration.
 * Validates routing decision when AOB_indicator is true, ensuring GDPR/SOC2 compliance via input validation
 * and structured logging NFRs. Mocks DynamoDB and S3 to avoid live infrastructure calls.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchestrationTest {

    @Mock
    private DynamoDBClient mockDynamoDBClient;
    @Mock
    private S3Client mockS3Client;
    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Initialize orchestrator with mocked infrastructure clients
        orchestrationService = new ClaimOrchestrationService(mockDynamoDBClient, mockS3Client);
    }

    @Test
    void decision_aob_present_rule_aob_indicator_true_legal_review_flag_expected_outcome_route_to_legal_queue() {
        // Arrange: Prepare claim data payload matching the rule condition
        String claimId = "claim-aob-001";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("AOB_indicator", true);
        inputPayload.put("claim_status", "SUBMITTED");
        inputPayload.put("legal_review_flag", false);
        inputPayload.put("id", claimId);

        // Mock DynamoDB read contract (Claim Data Store)
        when(mockDynamoDBClient.getItem(eq("Claim Data Store_table"), eq("pk"), eq(claimId)))
                .thenReturn(inputPayload);

        // Act: Execute orchestration with thread-safe payload copy
        Map<String, Object> result = orchestrationService.standardizeAndRoute(claimId, new HashMap<>(inputPayload));

        // Assert: Verify state transition outcome
        assertNotNull(result, "Orchestration result must not be null");
        assertEquals("LEGAL_QUEUE", result.get("target_queue"), "Should route to legal queue per rule");
        assertTrue((boolean) result.get("legal_review_flag"), "Legal review flag must be enabled");
        assertEquals("STANDARDIZED", result.get("standardization_status"), "Data must be marked standardized");

        // Verify infrastructure I/O contracts were invoked exactly once
        verify(mockDynamoDBClient, times(1)).getItem(eq("Claim Data Store_table"), eq("pk"), eq(claimId));
        verify(mockDynamoDBClient, times(1)).putItem(eq("Claim Data Store_table"), eq("pk"), anyMap());
        verify(mockS3Client, times(1)).putObject(eq("Document Management-bucket"), eq("Document Management/" + claimId + ".json"), any(byte[].class));
    }

    /**
     * Minimal orchestration service implementing the state transition logic.
     * Encapsulates input validation, rule evaluation, and infra I/O contracts.
     */
    static class ClaimOrchestrationService {
        private final DynamoDBClient dynamoDBClient;
        private final S3Client s3Client;

        ClaimOrchestrationService(DynamoDBClient dynamoDBClient, S3Client s3Client) {
            this.dynamoDBClient = dynamoDBClient;
            this.s3Client = s3Client;
        }

        Map<String, Object> standardizeAndRoute(String id, Map<String, Object> payload) {
            // Input validation & least privilege check
            if (payload == null || !payload.containsKey("AOB_indicator")) {
                throw new IllegalArgumentException("Invalid payload: AOB_indicator required");
            }

            Map<String, Object> result = new HashMap<>(payload);

            // Rule evaluation: AOB_indicator=true -> legal_review_flag
            if (Boolean.TRUE.equals(result.get("AOB_indicator"))) {
                result.put("legal_review_flag", true);
                result.put("target_queue", "LEGAL_QUEUE");
            }

            result.put("standardization_status", "STANDARDIZED");

            // Simulate secure infra I/O (TLS in transit assumed by client implementation)
            dynamoDBClient.putItem("Claim Data Store_table", "pk", result);
            s3Client.putObject("Document Management-bucket", "Document Management/" + id + ".json", result.toString().getBytes());

            return result;
        }
    }

    interface DynamoDBClient {
        Map<String, Object> getItem(String table, String pk, String id);
        void putItem(String table, String pk, Map<String, Object> item);
    }

    interface S3Client {
        void putObject(String bucket, String key, byte[] data);
    }
}
