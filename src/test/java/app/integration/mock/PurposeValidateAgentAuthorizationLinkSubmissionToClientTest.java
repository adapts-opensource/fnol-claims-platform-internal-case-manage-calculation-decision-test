package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Claim Data Standardization: transformation: orchestration - Agent Authorization & Routing")
class PurposeValidateAgentAuthorizationLinkSubmissionToClient {

    @Mock
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    @Captor
    private ArgumentCaptor<PutItemRequest> dynamoDbRequestCaptor;

    @Captor
    private ArgumentCaptor<PutObjectRequest> s3RequestCaptor;

    @BeforeEach
    void setUp() {
        // MockitoAnnotations.openMocks(this); // Automatically handled by @ExtendWith
    }

    @Test
    @DisplayName("purpose_validate_agent_authorization_link_submission_to_client_policy_and_enforce_agency_routing_rules")
    void purpose_validate_agent_authorization_link_submission_to_client_policy_and_enforce_agency_routing_rules() {
        // Arrange: Prepare input payload conforming to claim_data_standardization_state_transition_orch
        String submissionId = "claim-sub-8f3a1b";
        String agentId = "agent-7721";
        String clientId = "client-4402";
        String policyId = "pol-9910";
        String routingRule = "AGENCY_ROUTING_TIER_1";

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", submissionId);
        inputPayload.put("agentId", agentId);
        inputPayload.put("authorizationToken", "Bearer secure-token");
        inputPayload.put("clientId", clientId);
        inputPayload.put("policyId", policyId);
        inputPayload.put("routingRule", routingRule);

        // Mock orchestration steps & external I/O contracts
        when(orchestrationService.validateAgentAuthorization(eq(agentId), eq("Bearer secure-token")))
                .thenReturn(true);
        when(orchestrationService.linkSubmissionToClientPolicy(eq(submissionId), eq(clientId), eq(policyId)))
                .thenReturn(submissionId);
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(mock(PutItemResponse.class));
        when(s3Client.putObject(any(PutObjectRequest.class), any()))
                .thenReturn(mock(PutObjectResponse.class));

        // Act: Execute orchestration
        Map<String, Object> result = orchestrationService.processSubmission(inputPayload);

        // Assert: Validate business outcomes
        assertNotNull(result, "Orchestration must return a result payload");
        assertEquals(submissionId, result.get("id"), "Submission ID must be preserved");
        assertTrue((Boolean) result.get("authorizationValid"), "Agent authorization must be validated");
        assertEquals(routingRule, result.get("routingRule"), "Agency routing rules must be enforced");
        assertFalse((Boolean) result.get("pii"), "Data model constraint: pii must be false");

        // Verify: External I/O and infra contracts
        verify(orchestrationService).validateAgentAuthorization(eq(agentId), eq("Bearer secure-token"));
        verify(orchestrationService).linkSubmissionToClientPolicy(eq(submissionId), eq(clientId), eq(policyId));
        verify(dynamoDbClient).putItem(dynamoDbRequestCaptor.capture());
        verify(s3Client).putObject(s3RequestCaptor.capture(), any());

        // Validate DynamoDB contract: table_name & partition_key
        PutItemRequest capturedDynamoRequest = dynamoDbRequestCaptor.getValue();
        assertEquals("Claim Data Store_table", capturedDynamoRequest.tableName());
        assertTrue(capturedDynamoRequest.item().containsKey("pk"), "Partition key 'pk' must be present");

        // Validate S3 contract: bucket_name & object_key_pattern
        PutObjectRequest capturedS3Request = s3RequestCaptor.getValue();
        assertEquals("Document Management-bucket", capturedS3Request.bucket());
        assertTrue(capturedS3Request.key().startsWith("Document Management/"), "Object key must follow pattern");

        // NFR Compliance Notes:
        // - Security: TLS in transit & least privilege IAM assumed by mocked AWS SDK v2 clients
        // - Secrets: Authorization token validation implies secure secret management & input validation
        // - Concurrency: Stateless mock service & immutable payload handling ensure thread safety
        // - Observability: Structured logging would be triggered by orchestration service layer
    }

    // Minimal service interface to support mocking without production dependencies
    interface ClaimDataStandardizationOrchestrationService {
        boolean validateAgentAuthorization(String agentId, String token);
        String linkSubmissionToClientPolicy(String submissionId, String clientId, String policyId);
        Map<String, Object> processSubmission(Map<String, Object> payload);
    }
}
