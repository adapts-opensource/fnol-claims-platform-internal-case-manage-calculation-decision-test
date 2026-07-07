package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActiveLitigationOrMediationBlocksAutoMergeTest {

    @Mock
    private InsuredEngagementStatusService engagementStatusService;

    @InjectMocks
    private OrchestrationDecisionService orchestrationDecisionService;

    @Test
    void active_litigation_or_mediation_blocks_auto_merge() {
        // Arrange: Mock external engagement tracking to simulate active litigation/mediation
        String claimId = "CLM-8821";
        when(engagementStatusService.isEngagementBlockedForAutoMerge(claimId)).thenReturn(true);

        // Act: Trigger the orchestration decision logic
        boolean autoMergeEligible = orchestrationDecisionService.evaluateAutoMerge(claimId);

        // Assert: Verify auto-merge is explicitly blocked per compliance & risk NFRs
        assertFalse(autoMergeEligible, "Auto-merge must be blocked when active litigation or mediation is detected");
        verify(engagementStatusService, times(1)).isEngagementBlockedForAutoMerge(claimId);
    }
}
