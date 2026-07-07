package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimDataStandardizationDecisionValidationTest {

    @Mock
    private ClaimTaskLookupService claimTaskLookupService;

    private ClaimDataStandardizationDecisionValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new ClaimDataStandardizationDecisionValidator(claimTaskLookupService);
    }

    @Test
    void claimTaskExists() {
        // Arrange: Mock DynamoDB/S3 response for an existing claim/task
        String taskId = "claim-task-001";
        Map<String, Object> mockPayload = Map.of(
                "id", taskId,
                "status", "ACTIVE",
                "payload", Map.of("claimType", "FNOL", "priority", "HIGH")
        );
        when(claimTaskLookupService.resolveTaskById(taskId)).thenReturn(Optional.of(mockPayload));

        // Act: Trigger validation decision logic
        boolean isTaskValid = validator.decideValidationStatus(taskId);

        // Assert: Confirm decision acknowledges existing claim/task
        assertTrue(isTaskValid, "Validation decision should pass when claim/task exists");
        verify(claimTaskLookupService, times(1)).resolveTaskById(taskId);
    }
}
