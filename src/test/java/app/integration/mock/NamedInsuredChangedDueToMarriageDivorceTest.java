package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolStateTransitionCalculationTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private SesClient sesClient;
    @Mock
    private DynamoDbClient dynamoDbClient;

    private StateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new StateTransitionCalculator(s3Client, sesClient, dynamoDbClient);
    }

    @Test
    void named_insured_changed_due_to_marriage_divorce() {
        // Arrange
        String entityId = "multi-channel-fnol-marriage-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", entityId);
        payload.put("namedInsured", Map.of(
                "previousName", "Jane Doe",
                "newName", "Jane Smith",
                "maritalChangeReason", "MARRIAGE"
        ));
        payload.put("claimStatus", "INTAKE_COMPLETE");
        payload.put("channel", "MOBILE_APP");

        when(dynamoDbClient.getItem(anyString(), anyString())).thenReturn(payload);
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(Map.of("Attributes", Map.of("status", "REVIEW_PENDING")));
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(Map.of("MessageId", "ses-msg-789"));
        when(s3Client.putObject(any(PutObjectRequest.class), any())).thenReturn(Map.of("ETag", "\"abc123\""));

        // Act
        Map<String, Object> transitionResult = calculator.calculate(entityId, payload);

        // Assert
        assertEquals("REVIEW_PENDING", transitionResult.get("claimStatus"));
        assertEquals("CALCULATED", transitionResult.get("transitionState"));
        assertNotNull(transitionResult.get("calculationTimestamp"));
        assertTrue((boolean) transitionResult.get("requiresManualReview"));

        verify(dynamoDbClient, times(1)).putItem(any(PutItemRequest.class));
        verify(sesClient, times(1)).sendEmail(any(SendEmailRequest.class));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any());
    }

    /**
     * Minimal stub for the state transition calculation service.
     * In a real codebase, this would reside in app.domain.fnol.service.
     */
    static class StateTransitionCalculator {
        private final S3Client s3Client;
        private final SesClient sesClient;
        private final DynamoDbClient dynamoDbClient;

        StateTransitionCalculator(S3Client s3Client, SesClient sesClient, DynamoDbClient dynamoDbClient) {
            this.s3Client = s3Client;
            this.sesClient = sesClient;
            this.dynamoDbClient = dynamoDbClient;
        }

        Map<String, Object> calculate(String entityId, Map<String, Object> payload) {
            Map<String, Object> result = new HashMap<>(payload);
            result.put("calculationTimestamp", Instant.now().toString());
            result.put("transitionState", "CALCULATED");
            
            // Simulate business rule: marriage/divorce triggers manual review
            Map<String, Object> insured = (Map<String, Object>) payload.get("namedInsured");
            if (insured != null && insured.containsKey("maritalChangeReason")) {
                result.put("claimStatus", "REVIEW_PENDING");
                result.put("requiresManualReview", true);
                
                // Mock infra I/O
                try {
                    dynamoDbClient.putItem(PutItemRequest.builder()
                            .tableName("Data_Store_table")
                            .item(Map.of("id", Map.of("S", entityId), "status", Map.of("S", "REVIEW_PENDING")))
                            .build());
                    
                    sesClient.sendEmail(SendEmailRequest.builder()
                            .destination(Map.of("ToAddresses", List.of("claims@newco-insurance.com")))
                            .message(Map.of("Subject", Map.of("Data", Map.of("S", "FNOL Review Required"))))
                            .build());
                    
                    s3Client.putObject(PutObjectRequest.builder()
                            .bucket("Claim_Intake_Service-bucket")
                            .key("Claim_Intake_Service/" + entityId + ".json")
                            .build(), null);
                } catch (Exception e) {
                    throw new RuntimeException("Infra I/O failed during state transition calculation", e);
                }
            }
            return result;
        }
    }
}
