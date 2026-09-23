package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Multi-Channel FNOL Submission state transition calculation.
 * Verifies that cancellation logic is skipped when Date of Loss precedes Cancellation Date.
 */
@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionStateTransitionCalculationMockTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private SesClient sesClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private StateTransitionCalculator stateTransitionCalculator;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Test
    void cancellation_does_not_apply_if_dol_is_before_cancellation_date() {
        // Arrange
        String entityId = "fnol-submission-998";
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 1);
        LocalDate cancellationDate = LocalDate.of(2023, 10, 15);
        String initialStatus = "SUBMITTED";

        Map<String, AttributeValue> payloadAttributes = Map.of(
            "dateOfLoss", AttributeValue.builder().s(dateOfLoss.toString()).build(),
            "cancellationDate", AttributeValue.builder().s(cancellationDate.toString()).build(),
            "claimStatus", AttributeValue.builder().s(initialStatus).build()
        );

        Map<String, AttributeValue> itemAttributes = Map.of(
            "id", AttributeValue.builder().s(entityId).build(),
            "payload", AttributeValue.builder().m(payloadAttributes).build()
        );

        // Mock DynamoDB retrieval
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().item(itemAttributes).build());

        // Act
        Map<String, Object> resultPayload = stateTransitionCalculator.calculate(entityId, payloadAttributes);

        // Assert
        assertNotNull(resultPayload, "Result payload should not be null");
        assertEquals(initialStatus, resultPayload.get("claimStatus"),
            "Status should remain SUBMITTED as cancellation does not apply");
        assertFalse((Boolean) resultPayload.get("cancellationApplied"),
            "Cancellation flag should be false");

        // Verify no SES communication triggered
        verifyNoInteractions(sesClient, "SES should not be called when cancellation does not apply");

        // Verify persistence update
        verify(dynamoDbClient).updateItem(any());
    }
}
