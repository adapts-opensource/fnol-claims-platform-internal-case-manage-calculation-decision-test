package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolStateTransitionCalculationMockTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;

    private FnolStateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new FnolStateTransitionCalculator(s3Client, dynamoDbClient, sesClient);
    }

    @Test
    void dol_exactly_on_effective_date() {
        // Given: DoL exactly on effective date
        String claimId = "FNOL-2024-001";
        LocalDate effectiveDate = LocalDate.of(2024, 9, 15);
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "effectiveDate", effectiveDate.toString(),
            "dateOfLoss", effectiveDate.toString(),
            "channel", "WEB",
            "status", "INITIATED"
        );

        // Mock infra I/O contracts (S3, DynamoDB, SES)
        when(dynamoDbClient.putItem(any())).thenReturn(null);
        when(s3Client.putObject(any(), any())).thenReturn(null);
        when(sesClient.sendEmail(any())).thenReturn(null);

        // When: Trigger state transition calculation
        Map<String, Object> result = calculator.process(payload);

        // Then: Verify state transition logic, payload mutation, and infra calls
        assertNotNull(result);
        assertEquals("CALCULATED", result.get("status"));
        assertTrue((Boolean) result.get("stateTransitionValid"));
        assertEquals(effectiveDate.toString(), result.get("resolvedEffectiveDate"));
        verify(dynamoDbClient, times(1)).putItem(any());
        verify(s3Client, times(1)).putObject(any(), any());
        verify(sesClient, times(1)).sendEmail(any());
    }

    // Minimal interfaces to satisfy compiler without AWS SDK version conflicts
    interface S3Client {
        void putObject(Object request, Object payload);
    }

    interface DynamoDbClient {
        void putItem(Object request);
    }

    interface SesClient {
        void sendEmail(Object request);
    }

    // Service under test: simulates state_transition:calculation logic
    static class FnolStateTransitionCalculator {
        private final S3Client s3Client;
        private final DynamoDbClient dynamoDbClient;
        private final SesClient sesClient;

        FnolStateTransitionCalculator(S3Client s3Client, DynamoDbClient dynamoDbClient, SesClient sesClient) {
            this.s3Client = s3Client;
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
        }

        Map<String, Object> process(Map<String, Object> payload) {
            LocalDate effDate = LocalDate.parse((String) payload.get("effectiveDate"));
            LocalDate dolDate = LocalDate.parse((String) payload.get("dateOfLoss"));

            // Business rule: DoL must be on or before Effective Date
            boolean validTransition = dolDate.isEqual(effDate) || dolDate.isBefore(effDate);

            Map<String, Object> result = Map.of(
                "id", payload.get("id"),
                "status", validTransition ? "CALCULATED" : "REJECTED",
                "stateTransitionValid", validTransition,
                "resolvedEffectiveDate", effDate.toString(),
                "processedAt", LocalDate.now().toString()
            );

            // Simulate structured logging & infra I/O contracts
            dynamoDbClient.putItem(result);
            s3Client.putObject(result, result);
            sesClient.sendEmail(result);

            return result;
        }
    }
}
