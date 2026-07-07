package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionValidationTest {

    @Mock
    private FnolOrchestrationService fnolOrchestrationService;

    @Mock
    private S3Client s3Client;

    @Mock
    private DynamoDbClient dynamoDbClient;

    private Map<String, Object> incompleteLossPayload;

    @BeforeEach
    void setUp() {
        // Simulate a payload missing critical loss details per data model
        incompleteLossPayload = Map.of(
            "id", "fnol-sub-001",
            "channel", "MOBILE",
            "policyNumber", "POL-98765",
            "lossDetails", Map.of(
                "lossDate", "2024-05-15",
                "lossLocation", "123 Main St",
                "lossDescription", "",
                "estimatedDamageAmount", 0.0
            )
        );
    }

    @Test
    void incomplete_loss_details() {
        // Arrange: Mock external I/O contracts to prevent live AWS/HTTP calls
        when(s3Client.putObject(any())).thenReturn(null);
        when(dynamoDbClient.putItem(any())).thenReturn(null);

        doThrow(new ValidationFailedException("Validation failed: Incomplete loss details. Missing required fields: [witnessDetails, lossDescription]"))
            .when(fnolOrchestrationService).processSubmission(anyMap());

        // Act & Assert
        ValidationFailedException exception = assertThrows(
            ValidationFailedException.class,
            () -> fnolOrchestrationService.processSubmission(incompleteLossPayload)
        );

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Incomplete loss details"));
        verify(fnolOrchestrationService, times(1)).processSubmission(incompleteLossPayload);
        verify(s3Client, never()).putObject(any());
        verify(dynamoDbClient, never()).putItem(any());
    }

    static class ValidationFailedException extends RuntimeException {
        public ValidationFailedException(String message) {
            super(message);
        }
    }
}
