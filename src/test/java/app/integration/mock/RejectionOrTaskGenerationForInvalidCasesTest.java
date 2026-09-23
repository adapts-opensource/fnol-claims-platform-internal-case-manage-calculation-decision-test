package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationTest {

    @Mock
    private ClaimDataValidationService validationService;

    @Mock
    private ClaimDataStoreClient claimDataStore;

    @Mock
    private DocumentManagementClient documentManagement;

    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimDataStandardizationOrchestrationService(
                validationService, claimDataStore, documentManagement
        );
    }

    @Test
    @DisplayName("rejection_or_task_generation_for_invalid_cases")
    void rejectionOrTaskGenerationForInvalidCases() {
        // Arrange
        String claimId = "claim-123-invalid";
        Map<String, Object> invalidPayload = new HashMap<>();
        invalidPayload.put("policyNumber", "");
        invalidPayload.put("claimDate", "not-a-date");
        invalidPayload.put("description", null);

        when(validationService.validate(claimId, invalidPayload)).thenReturn(false);
        doNothing().when(claimDataStore).updateStatus(eq(claimId), eq("REJECTED"));
        doNothing().when(documentManagement).createTask(eq(claimId), anyMap());

        // Act
        assertDoesNotThrow(() -> orchestrationService.processClaimData(claimId, invalidPayload));

        // Assert
        verify(validationService).validate(claimId, invalidPayload);
        verify(claimDataStore, atLeastOnce()).updateStatus(eq(claimId), eq("REJECTED"));
        verify(documentManagement, atLeastOnce()).createTask(eq(claimId), anyMap());
    }
}
