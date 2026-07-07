package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MultipleMatchesTriggerResolvePolicyMatchTaskTest {

    @Mock
    private PolicyMatchFinder policyMatchFinder;

    @Mock
    private TaskScheduler taskScheduler;

    private InsuredEngagementProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InsuredEngagementProcessor(policyMatchFinder, taskScheduler);
    }

    @Test
    void multiple_matches_trigger_resolve_policy_match_task() {
        // Arrange
        String claimId = "CLM-2024-001";
        String insuredId = "INS-88990";
        List<PolicyMatch> multipleMatches = List.of(
            new PolicyMatch("POL-A-123", "ACTIVE"),
            new PolicyMatch("POL-B-456", "ACTIVE"),
            new PolicyMatch("POL-C-789", "PENDING")
        );

        when(policyMatchFinder.findMatchingPolicies(claimId, insuredId))
            .thenReturn(multipleMatches);

        // Act
        processor.transformDecision(claimId, insuredId);

        // Assert
        verify(taskScheduler, times(1))
            .createResolvePolicyMatchTask(claimId, insuredId, multipleMatches);
    }

    // Test doubles for isolated compilation
    interface PolicyMatchFinder {
        List<PolicyMatch> findMatchingPolicies(String claimId, String insuredId);
    }

    interface TaskScheduler {
        void createResolvePolicyMatchTask(String claimId, String insuredId, List<PolicyMatch> matches);
    }

    record PolicyMatch(String policyId, String status) {}
}
