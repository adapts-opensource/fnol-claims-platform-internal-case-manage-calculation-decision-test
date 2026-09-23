package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    @Mock
    private DataStoreClient dataStoreClient;
    @Mock
    private S3Client s3Client;
    @Mock
    private SESClient sesClient;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    private MultiChannelFnolSubmissionStateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MultiChannelFnolSubmissionStateTransitionCalculator(
            dataStoreClient, s3Client, sesClient
        );
    }

    @Test
    void applies_when_fnol_submission_includes_date_of_loss_and_policy_match_exists() {
        // Arrange
        String submissionId = UUID.randomUUID().toString();
        String policyId = "POL-789012";
        LocalDate dateOfLoss = LocalDate.now().minusDays(2);

        Map<String, Object> submissionPayload = new HashMap<>();
        submissionPayload.put("id", submissionId);
        submissionPayload.put("policyId", policyId);
        submissionPayload.put("dateOfLoss", dateOfLoss.toString());
        submissionPayload.put("channel", "WEB");

        // Mock policy match lookup per DynamoDB contract
        when(dataStoreClient.getItem(eq("Data Store_table"), eq("pk"), eq(policyId)))
            .thenReturn(Map.of("policyId", policyId, "status", "ACTIVE"));

        // Mock S3 write per S3 contract
        when(s3Client.putObject(anyString(), anyString(), any(Map.class)))
            .thenReturn("s3://Claim-Intake-Service-bucket/fnol/" + submissionId + ".json");

        // Act
        Map<String, Object> result = calculator.processSubmission(submissionPayload);

        // Assert
        assertNotNull(result);
        assertEquals("CALCULATION_APPLIED", result.get("state"));
        assertEquals(submissionId, result.get("id"));
        assertEquals(dateOfLoss.toString(), result.get("dateOfLoss"));

        // Verify interactions
        verify(dataStoreClient).getItem(eq("Data Store_table"), eq("pk"), eq(policyId));
        verify(s3Client).putObject(eq("Claim Intake Service-bucket"), eq("Claim Intake Service/" + submissionId + ".json"), payloadCaptor.capture());
        verify(sesClient, never()).sendEmail(anyString(), anyList(), eq("us-east-1"));
    }

    // Minimal interface definitions to ensure compilation and mock isolation
    interface DataStoreClient {
        Map<String, Object> getItem(String tableName, String partitionKey, String key);
    }
    interface S3Client {
        String putObject(String bucketName, String objectKey, Map<String, Object> payload);
    }
    interface SESClient {
        String sendEmail(String fromAddress, List<String> toAddresses, String region);
    }
}
