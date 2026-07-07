package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private FnolSubmissionOrchestrator orchestrator;

    @Mock
    private FnolValidationService validationService;

    private static final String INVALID_AGENCY_ID = "INVALID_AGENCY_123";
    private Map<String, Object> submissionPayload;

    @BeforeEach
    void setUp() {
        submissionPayload = Map.of(
            "id", UUID.randomUUID().toString(),
            "payload", Map.of(
                "agencyId", INVALID_AGENCY_ID,
                "channel", "WEB",
                "claimDetails", Map.of("dateOfLoss", "2023-10-25T10:00:00Z")
            )
        );
    }

    @Test
    void invalidAgencyId() {
        // Arrange: Mock validation layer to enforce input_validation NFR
        when(validationService.validateAgencyId(INVALID_AGENCY_ID))
            .thenThrow(new IllegalArgumentException("Validation failed: Invalid agency ID provided."));

        // Act & Assert: Verify orchestration rejects invalid agency ID before state transition
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.processSubmission(submissionPayload);
        });

        verify(validationService).validateAgencyId(INVALID_AGENCY_ID);
        // Ensure no downstream I/O (S3, SES, DynamoDB) occurs on validation failure
        verifyNoInteractions(orchestrator);
    }
}
