package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemCorrectlyValidatesDolAgainstAllPolicyDatesTest {

    @Mock private S3Client s3Client;
    @Mock private SesClient sesClient;
    @Mock private DynamoDbClient dynamoDbClient;
    @Mock private HttpClient httpClient;

    @InjectMocks
    private FnolStateTransitionCalculator calculator;

    @Test
    void system_correctly_validates_dol_against_all_policy_dates() {
        // Given: Payload mimicking multi_channel_fnol_submission_state_transition_c entity
        Map<String, Object> payload = Map.of(
            "id", "test-fnol-id",
            "dateOfLoss", LocalDate.of(2023, 10, 15),
            "policyEffectiveDate", LocalDate.of(2023, 1, 1),
            "policyExpirationDate", LocalDate.of(2024, 1, 1),
            "policyInceptionDate", LocalDate.of(2023, 1, 1)
        );

        // Mock external I/O contracts (S3, SES, DynamoDB, HTTP) to prevent live calls
        when(s3Client.putObject(any(), any(), any())).thenReturn(null);
        when(sesClient.sendEmail(any())).thenReturn(null);
        when(dynamoDbClient.putItem(any())).thenReturn(null);
        when(httpClient.newHttpClient()).thenReturn(httpClient);

        // When: Execute state transition calculation
        boolean isValid = calculator.calculateStateTransition(payload);

        // Then: Assert DoL is correctly validated against all policy dates
        assertTrue(isValid, "System should correctly validate DoL against all policy dates");
        verify(s3Client, times(1)).putObject(any(), any(), any());
        verify(dynamoDbClient, times(1)).putItem(any());
    }
}
