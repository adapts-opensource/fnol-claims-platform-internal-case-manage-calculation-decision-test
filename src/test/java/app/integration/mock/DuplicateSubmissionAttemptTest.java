package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 integration mock test for Multi-Channel FNOL Submission:orchestration:validation.
 * Verifies DuplicateSubmissionAttempt behavior including idempotency checks,
 * validation rejection, and prevention of side effects (S3/SES) on duplicates.
 */
@ExtendWith(MockitoExtension.class)
class DuplicateSubmissionAttemptTest {

    @Mock
    private FnolSubmissionRepository submissionRepository;

    @Mock
    private IdempotencyCheckService idempotencyCheckService;

    @Mock
    private ClaimIntakeService claimIntakeService;

    @Mock
    private CommunicationsHandler communicationsHandler;

    @InjectMocks
    private FnolSubmissionOrchestrator orchestrator;

    private static final String TEST_REQUEST_ID = "req-dup-001";
    private static final String TEST_POLICY_ID = "POL-999";
    private static final Map<String, Object> TEST_PAYLOAD = Map.of(
            "policyId", TEST_POLICY_ID,
            "incidentDate", "2024-01-15",
            "channel", "WEB_API"
    );

    @BeforeEach
    void setUp() {
        // Reset mocks between tests to ensure isolation
        reset(submissionRepository, idempotencyCheckService, claimIntakeService, communicationsHandler);
    }

    @Test
    void duplicateSubmissionAttempt() {
        // Given: A duplicate submission is detected by the idempotency service
        when(idempotencyCheckService.isDuplicate(TEST_REQUEST_ID)).thenReturn(true);
        
        // Mock existing record retrieval for validation context
        SubmissionState existingRecord = new SubmissionState(TEST_REQUEST_ID, TEST_PAYLOAD);
        when(submissionRepository.findByRequestId(TEST_REQUEST_ID))
                .thenReturn(Optional.of(existingRecord));

        // When: Attempting to process the submission
        SubmissionResult result = orchestrator.processSubmission(TEST_PAYLOAD, TEST_REQUEST_ID);

        // Then: Verify rejection with appropriate status and error code
        assertNotNull(result, "Result should not be null");
        assertEquals(SubmissionOutcome.REJECTED, result.getOutcome(), 
                "Submission should be rejected due to duplicate detection");
        assertEquals("DUPLICATE_SUBMISSION", result.getErrorCode(), 
                "Error code should indicate duplicate submission");
        assertTrue(result.getMessage().contains(TEST_REQUEST_ID), 
                "Error message should reference the duplicate request ID");

        // Verify validation logic was invoked
        verify(idempotencyCheckService, times(1)).isDuplicate(TEST_REQUEST_ID);

        // Verify no persistence or side effects occurred (NFR: Data Integrity, Security)
        verify(submissionRepository, never()).save(any());
        verifyNoInteractions(claimIntakeService, "Claim Intake S3 write should not occur on duplicate");
        verifyNoInteractions(communicationsHandler, "Communication SES send should not occur on duplicate");
    }
}
