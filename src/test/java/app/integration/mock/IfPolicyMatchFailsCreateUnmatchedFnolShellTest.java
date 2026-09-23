package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private PolicyMatchingService policyMatchingService;
    @Mock
    private ClaimDataStoreService claimDataStoreService;

    private ClaimDataStandardizationOrchestration orchestration;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestration = new ClaimDataStandardizationOrchestration(policyMatchingService, claimDataStoreService);
    }

    @Test
    void if_policy_match_fails_create_unmatched_fnol_shell() {
        // Arrange: Simulate policy resolution failure to trigger unmatched shell creation
        String fnolId = "FNOL-2024-001";
        Map<String, Object> rawPayload = Map.of(
            "claimType", "AUTO_COLLISION",
            "incidentDate", "2024-05-20",
            "policyNumber", "POL-9999"
        );
        when(policyMatchingService.resolve(anyString())).thenReturn(false);

        // Act: Execute orchestration with input validation and structured logging context
        orchestration.standardizeAndOrchestrate(fnolId, rawPayload);

        // Assert: Verify Unmatched FNOL shell is persisted to DynamoDB with correct state transition
        Map<String, Object> expectedShell = Map.of(
            "id", fnolId,
            "state", "UNMATCHED",
            "payload", rawPayload,
            "processingStatus", "FAILED_POLICY_MATCH",
            "createdAt", "2024-05-20T00:00:00Z"
        );

        verify(claimDataStoreService, times(1))
            .putItem(eq("Claim Data Store_table"), eq("pk"), eq(expectedShell));
        verify(policyMatchingService, times(1)).resolve(anyString());
        verifyNoMoreInteractions(claimDataStoreService);
    }
}
