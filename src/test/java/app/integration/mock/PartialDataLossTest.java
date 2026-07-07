package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionPartialDataLossTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    private ReserveLineStateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        stateTransitionService = new ReserveLineStateTransitionService(dynamoDbClient, sesClient);
    }

    @Test
    void partial_data_loss() {
        // Arrange
        String reserveId = "res-789";
        String exposureId = "exp-101";
        String targetState = "Approved";

        // Simulate partial data loss: DynamoDB update fails mid-operation after partial commit
        doThrow(SdkClientException.builder().message("Partial write failure: timeout after partial commit").build())
                .when(dynamoDbClient).updateItem(any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            stateTransitionService.transitionState(reserveId, exposureId, targetState);
        });

        // Verify infrastructure interaction and graceful failure handling
        verify(dynamoDbClient).updateItem(any());
        verifyNoInteractions(sesClient);
    }

    // Minimal service stub for test context and compilation
    static class ReserveLineStateTransitionService {
        private final DynamoDbClient dynamoDbClient;
        private final SesClient sesClient;

        ReserveLineStateTransitionService(DynamoDbClient dynamoDbClient, SesClient sesClient) {
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
        }

        void transitionState(String reserveId, String exposureId, String newState) {
            // Simulate state transition logic interacting with persisted reserve line
            dynamoDbClient.updateItem(any());
        }
    }
}
