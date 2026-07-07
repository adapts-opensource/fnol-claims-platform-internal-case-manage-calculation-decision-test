package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

// Infrastructure mocks for AWS services
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.ResponseInputStream;

/**
 * Mock test for Multi-Channel FNOL Submission:state_transition:calculation.
 * Verifies state transition logic and scoring based on policy period constraints.
 */
public class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    private StateTransitionCalculationService service;
    private DynamoDbClient dynamoDbMock;
    private S3Client s3Mock;

    @BeforeEach
    void setUp() {
        dynamoDbMock = Mockito.mock(DynamoDbClient.class);
        s3Mock = Mockito.mock(S3Client.class);
        service = new StateTransitionCalculationService(dynamoDbMock, s3Mock);
    }

    @Test
    void date_of_loss_must_be_within_policy_period_for_high_score() {
        // Given: Payload with date of loss strictly within policy period
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateOfLoss", "2023-06-15");
        payload.put("policyStartDate", "2023-01-01");
        payload.put("policyEndDate", "2023-12-31");
        payload.put("channel", "WEB");
        payload.put("submissionId", "fnol-12345");

        // Mock Policy Data Store lookup via DynamoDB
        Map<String, AttributeValue> policyData = new HashMap<>();
        policyData.put("startDate", AttributeValue.builder().s("2023-01-01").build());
        policyData.put("endDate", AttributeValue.builder().s("2023-12-31").build());
        
        when(dynamoDbMock.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().item(policyData).build());

        // Mock S3 for claim document retrieval (simulating multi-channel intake)
        ResponseInputStream<?> mockStream = Mockito.mock(ResponseInputStream.class);
        when(mockStream.responseStream()).thenReturn(null); 
        when(s3Mock.getObject(any(GetObjectRequest.class))).thenReturn(mockStream);

        // When: Calculation is triggered
        CalculationResult result = service.calculate(payload);

        // Then: Score must be HIGH and state transitioned
        assertNotNull(result, "Result should not be null");
        assertEquals("HIGH", result.getScore(), "Score should be HIGH when loss is within policy period");
        assertTrue(result.isEligibleForFastTrack(), "Should be eligible for fast track processing");
        assertEquals("TRANSITIONED", result.getState(), "State should transition to CALCULATED");
    }
}
