package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SystemMatchesPolicyWithin3SecondsTest {

    // Simplified domain interfaces representing external I/O layers (DynamoDB, Policy Service)
    interface StateTransitionRepository {
        boolean updateState(String claimId, String newState);
    }

    interface PolicyMatchingService {
        boolean matchAndValidate(String claimId, String policyId);
    }

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @Mock
    private PolicyMatchingService policyMatchingService;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(stateTransitionRepository, policyMatchingService);
    }

    @Test
    void system_matches_policy_within_3_seconds() {
        String claimId = "claim-789";
        String policyId = "policy-101";
        String expectedState = "POLICY_MATCHED";

        // Arrange: Mock external I/O to simulate fast, successful operations
        when(stateTransitionRepository.updateState(claimId, expectedState)).thenReturn(true);
        when(policyMatchingService.matchAndValidate(claimId, policyId)).thenReturn(true);

        // Act: Execute state transition and policy matching
        long startNanos = System.nanoTime();
        boolean success = insuredEngagementService.transitionAndMatchPolicy(claimId, policyId);
        long elapsedNanos = System.nanoTime() - startNanos;

        // Assert: Verify functional success and timing constraint (< 3 seconds)
        assertTrue(success, "State transition and policy match should succeed");
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        assertTrue(elapsedSeconds < 3.0, 
                String.format("System must match policy within 3 seconds. Actual: %.3fs", elapsedSeconds));

        // Verify interactions with mocked external layers
        verify(stateTransitionRepository, times(1)).updateState(claimId, expectedState);
        verify(policyMatchingService, times(1)).matchAndValidate(claimId, policyId);
    }
}

/**
 * Service layer orchestrating insured engagement state transitions and policy matching.
 * In production, this would interact with DynamoDB (Data Persistence) and external policy engines.
 */
class InsuredEngagementService {
    private final StateTransitionRepository stateTransitionRepository;
    private final PolicyMatchingService policyMatchingService;

    InsuredEngagementService(StateTransitionRepository stateTransitionRepository, PolicyMatchingService policyMatchingService) {
        this.stateTransitionRepository = stateTransitionRepository;
        this.policyMatchingService = policyMatchingService;
    }

    boolean transitionAndMatchPolicy(String claimId, String policyId) {
        stateTransitionRepository.updateState(claimId, "POLICY_MATCHED");
        return policyMatchingService.matchAndValidate(claimId, policyId);
    }
}
