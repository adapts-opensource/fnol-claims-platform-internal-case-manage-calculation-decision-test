package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationCalculationTransformTest {

    @Mock
    private S3Client auditDiaryStoreS3;

    @Mock
    private DynamoDbClient rulesEngineDecisionServiceDynamoDb;

    @Mock
    private DynamoDbClient workflowTaskRouterDynamoDb;

    private ClaimDataStandardizationCalculationTransformService transformService;

    @BeforeEach
    void setUp() {
        transformService = new ClaimDataStandardizationCalculationTransformService(
                auditDiaryStoreS3,
                rulesEngineDecisionServiceDynamoDb,
                workflowTaskRouterDynamoDb
        );
    }

    @Test
    void matching_algorithm_returns_correct_policy_for_exact_inputs() {
        // Arrange
        String claimId = "claim-123";
        Map<String, Object> payload = new HashMap<>();
        payload.put("policyNumber", "POL-EXACT-001");
        payload.put("claimAmount", 5000.0);
        payload.put("incidentDate", "2023-10-25");

        Map<String, AttributeValue> expectedItem = new HashMap<>();
        expectedItem.put("policyId", AttributeValue.builder().s("POL-EXACT-001").build());
        expectedItem.put("status", AttributeValue.builder().s("ACTIVE").build());
        expectedItem.put("coverageLimit", AttributeValue.builder().s("10000.0").build());

        when(rulesEngineDecisionServiceDynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(expectedItem).build());

        when(auditDiaryStoreS3.putObject(any(PutObjectRequest.class), any()))
                .thenReturn(null);

        // Act
        Map<String, Object> result = transformService.transformAndMatch(claimId, payload);

        // Assert
        assertNotNull(result, "Transformed result should not be null");
        assertEquals("POL-EXACT-001", result.get("policyId"), "Policy ID must match exact input");
        assertEquals("ACTIVE", result.get("status"), "Policy status should be correctly standardized");
        assertEquals("10000.0", result.get("coverageLimit"), "Coverage limit should be transformed accurately");

        // Verify infrastructure I/O contracts
        verify(rulesEngineDecisionServiceDynamoDb, times(1)).getItem(any(GetItemRequest.class));
        verify(auditDiaryStoreS3, times(1)).putObject(any(PutObjectRequest.class), any());
    }
}
