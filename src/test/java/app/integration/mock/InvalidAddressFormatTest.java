package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvalidAddressFormatTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    @InjectMocks
    private FnolOrchestrationValidator validator;

    private Map<String, Object> invalidPayload;

    @BeforeEach
    void setUp() {
        invalidPayload = Map.of(
            "id", "fnol-123",
            "address", "123 Invalid Street, #Apt, City, State"
        );
    }

    @Test
    @DisplayName("invalid_address_format")
    void invalidAddressFormat() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            validator.validateSubmission(invalidPayload);
        });

        assertTrue(exception.getMessage().toLowerCase().contains("invalid address format"));
        verifyNoInteractions(s3Client, dynamoDbClient, sesClient);
    }

    // Infra I/O Contract Stubs (Mocked to prevent live AWS calls)
    interface S3Client {
        void putObject(String bucketName, String objectKey, byte[] content);
    }

    interface DynamoDbClient {
        void putItem(String tableName, Map<String, Object> itemPayload);
    }

    interface SesClient {
        String sendEmail(String fromAddress, Map<String, String> toAddresses, String body);
    }

    // Service Under Test
    static class FnolOrchestrationValidator {
        private final S3Client s3Client;
        private final DynamoDbClient dynamoDbClient;
        private final SesClient sesClient;

        FnolOrchestrationValidator(S3Client s3Client, DynamoDbClient dynamoDbClient, SesClient sesClient) {
            this.s3Client = s3Client;
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
        }

        public void validateSubmission(Map<String, Object> payload) {
            String address = (String) payload.get("address");
            if (address == null || !address.matches("^[A-Za-z0-9\\s,.-]+$") || address.contains("#")) {
                throw new IllegalArgumentException("Invalid address format");
            }
            // Infra I/O would proceed here only after validation passes
        }
    }
}
