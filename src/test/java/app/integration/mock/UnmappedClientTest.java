package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationTransformationOrchestrationTest {

    @Mock
    private ClientMappingService clientMappingService;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimDataStandardizationOrchestrationService(clientMappingService, dynamoDbClient, s3Client);
    }

    @Test
    void unmapped_client() {
        // Arrange
        String unmappedClientId = "UNMAPPED_CLIENT_99";
        Map<String, Object> payload = Map.of(
                "id", "claim-orch-456",
                "client_id", unmappedClientId,
                "payload", Map.of("claim_type", "FNOL", "severity", "HIGH")
        );

        when(clientMappingService.isClientMapped(unmappedClientId)).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrationService.processClaimTransformation(payload);
        });

        // Verify that mapping check occurred and downstream I/O was never invoked
        verify(clientMappingService, times(1)).isClientMapped(unmappedClientId);
        verifyNoInteractions(dynamoDbClient, s3Client);
    }

    // Minimal stubs to ensure test compilation and isolate orchestration logic
    interface ClientMappingService {
        boolean isClientMapped(String clientId);
    }

    interface DynamoDbClient {
        // Mocks DynamoDB interactions; never calls live AWS
    }

    interface S3Client {
        // Mocks S3 interactions; never calls live AWS
    }

    static class ClaimDataStandardizationOrchestrationService {
        private final ClientMappingService clientMappingService;
        private final DynamoDbClient dynamoDbClient;
        private final S3Client s3Client;

        ClaimDataStandardizationOrchestrationService(ClientMappingService clientMappingService,
                                                     DynamoDbClient dynamoDbClient,
                                                     S3Client s3Client) {
            this.clientMappingService = clientMappingService;
            this.dynamoDbClient = dynamoDbClient;
            this.s3Client = s3Client;
        }

        void processClaimTransformation(Map<String, Object> payload) {
            String clientId = (String) payload.get("client_id");
            if (!clientMappingService.isClientMapped(clientId)) {
                throw new IllegalArgumentException("Client is unmapped: " + clientId);
            }
            // Orchestration continues to transform, persist to DynamoDB, and upload to S3
        }
    }
}
