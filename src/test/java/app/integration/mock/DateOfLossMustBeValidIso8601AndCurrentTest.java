package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    private ClaimStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Mock external I/O to prevent live AWS calls
        when(dynamoDbClient.putItem(anyString(), anyMap())).thenReturn(null);
        when(s3Client.putObject(anyString(), anyString(), any())).thenReturn(null);
        orchestrator = new ClaimStandardizationOrchestrator(dynamoDbClient, s3Client);
    }

    @Test
    void date_of_loss_must_be_valid_iso_8601_and_current_date() {
        // Case 1: Valid past date should pass transformation
        Map<String, Object> validPastPayload = Map.of("date_of_loss", "2023-05-15");
        assertDoesNotThrow(() -> orchestrator.transform(validPastPayload));

        // Case 2: Valid current date should pass transformation
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Map<String, Object> validCurrentPayload = Map.of("date_of_loss", today);
        assertDoesNotThrow(() -> orchestrator.transform(validCurrentPayload));

        // Case 3: Future date must fail validation
        String futureDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        Map<String, Object> futurePayload = Map.of("date_of_loss", futureDate);
        assertThrows(IllegalArgumentException.class, () -> orchestrator.transform(futurePayload));

        // Case 4: Invalid ISO-8601 format must fail parsing
        Map<String, Object> invalidPayload = Map.of("date_of_loss", "15/05/2023");
        assertThrows(DateTimeParseException.class, () -> orchestrator.transform(invalidPayload));
    }

    // Lightweight AWS client interfaces for mocking
    private interface DynamoDbClient {
        void putItem(String tableName, Map<String, Object> item);
    }

    private interface S3Client {
        void putObject(String bucketName, String objectKey, Object payload);
    }

    // Orchestrator under test
    private static class ClaimStandardizationOrchestrator {
        private final DynamoDbClient dynamoDbClient;
        private final S3Client s3Client;

        ClaimStandardizationOrchestrator(DynamoDbClient dynamoDbClient, S3Client s3Client) {
            this.dynamoDbClient = dynamoDbClient;
            this.s3Client = s3Client;
        }

        void transform(Map<String, Object> payload) {
            if (!payload.containsKey("date_of_loss")) {
                return;
            }
            String dateStr = (String) payload.get("date_of_loss");
            
            // Validate ISO-8601 format and current/past constraint
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            if (date.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("date_of_loss must be valid ISO-8601 and <= current date");
            }

            // Simulate infra I/O contracts per specification
            dynamoDbClient.putItem("Claim Data Store_table", Map.of("pk", "claim123", "payload", payload));
            s3Client.putObject("Document Management-bucket", "Document Management/claim123.json", payload);
        }
    }
}
