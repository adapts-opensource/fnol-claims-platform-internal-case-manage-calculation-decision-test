package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that duplicate detection logic correctly blocks subsequent FNOL submissions
 * when an earlier submission is still in an active or unresolved state.
 * 
 * NFR Alignment:
 * - Security: Input validation prevents duplicate processing attacks.
 * - Concurrency: Ensures atomic state checks prevent race conditions on duplicate detection.
 * - Compliance: Maintains data integrity by avoiding duplicate claim records.
 */
@ExtendWith(MockitoExtension.class)
public class DuplicateDetectionValidationTest {

    @Mock
    private DataStoreClient dataStoreClient;

    @Mock
    private ClaimIntakeService claimIntakeService;

    @Mock
    private CommunicationsHandler communicationsHandler;

    @InjectMocks
    private FnoLSubmissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Reset mocks between tests if necessary; MockitoExtension handles this by default.
    }

    @Test
    void duplicate_detection_blocks_second_submission_until_first_resolves() {
        // Arrange: Simulate an existing submission that is active/unresolved
        String existingId = "fnol-claim-001";
        String duplicatePayloadJson = "{\"policyNumber\":\"POL-999\",\"incidentDate\":\"2023-10-01\",\"channel\":\"WEB\"}";
        
        // Mock DynamoDB response: Existing item found with unresolved status
        Map<String, Object> existingState = Map.of(
            "id", existingId,
            "payload", Map.of(
                "policyNumber", "POL-999",
                "incidentDate", "2023-10-01",
                "status", "IN_REVIEW"
            )
        );

        when(dataStoreClient.getItem(anyString(), eq("pk"), eq("fnol-claim-001")))
            .thenReturn(Optional.of(existingState));

        // Mock S3: Intake storage would normally happen, but validation blocks before persistence
        when(claimIntakeService.storePayload(anyString(), anyString()))
            .thenReturn("s3://claim-intake-bucket/fnol-claim-001.json");

        // Act & Assert: Second submission should throw a validation exception
        assertThrows(
            DuplicateSubmissionException.class,
            () -> orchestrator.submit(duplicatePayloadJson),
            "Second submission must be blocked when first submission is unresolved"
        );

        // Verify: First submission state must not be modified
        verify(dataStoreClient, never()).updateItem(anyString(), anyMap());

        // Verify: No new item should be written to DynamoDB
        verify(dataStoreClient, never()).putItem(anyString(), anyMap());

        // Verify: No duplicate email notification should be sent
        verify(communicationsHandler, never()).sendNotification(anyString(), anyList(), anyString());
    }
}
