package app.integration.mock;

import app.domain.model.MultiChannelFnolSubmissionStateTransition;
import app.infrastructure.dynamodb.DynamoDbRepository;
import app.service.StateTransitionCalculationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for Multi-Channel FNOL Submission:state_transition:calculation.
 * Validates behavior during infrastructure failures and state transition logic.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    @Mock
    private DynamoDbRepository dynamoDbRepository;

    @InjectMocks
    private StateTransitionCalculationService calculationService;

    /**
     * Verifies that the system handles DynamoDB failures gracefully.
     * Ensures the service wraps infrastructure exceptions in a business exception
     * and does not leak raw AWS SDK errors to the caller.
     */
    @Test
    void system_handles_db_failures_gracefully() {
        // Arrange
        String submissionId = "SUB-GRACE-001";
        Map<String, Object> payload = Map.of(
            "claimType", "AUTO",
            "incidentDate", "2023-10-27",
            "channel", "WEB",
            "description", "Minor collision"
        );
        MultiChannelFnolSubmissionStateTransition entity = new MultiChannelFnolSubmissionStateTransition(submissionId, payload);

        // Simulate a DynamoDB service failure
        DynamoDbException dbFailure = DynamoDbException.builder()
            .message("Service unavailable: Connection timeout")
            .build();

        when(dynamoDbRepository.save(any()))
            .thenThrow(dbFailure);

        // Act & Assert
        // The system should catch the infrastructure exception and throw a graceful wrapper
        assertThrows(
            DataStoreOperationException.class,
            () -> calculationService.calculateAndPersist(entity),
            "System should handle DB failure gracefully by throwing a DataStoreOperationException"
        );

        // Verify the persistence attempt was made
        verify(dynamoDbRepository, times(1)).save(any());
    }
}
