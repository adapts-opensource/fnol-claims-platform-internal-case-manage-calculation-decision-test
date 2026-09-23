package app.integration.mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.integration.mock.client.ExternalPolicySystemClient;
import app.integration.mock.service.InsuredEngagementOrchestrationService;
import app.integration.mock.exception.PolicyTimeoutException;
import app.integration.mock.model.ClaimId;
import app.integration.mock.model.DecisionStatus;
import app.integration.mock.repository.ClaimRepository;

/**
 * JUnit 5 mock test class for Insured Engagement & Tracking orchestration decision feature.
 * Verifies handling of external policy system timeouts within the orchestration flow.
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementOrchestrationDecisionTest {

    private static final String CLAIM_ID_VALUE = "CLM-8842-ORCH-001";
    private static final Duration POLICY_TIMEOUT_THRESHOLD = Duration.ofSeconds(5);

    @Mock
    private ExternalPolicySystemClient policySystemClient;

    @Mock
    private ClaimRepository claimRepository;

    @InjectMocks
    private InsuredEngagementOrchestrationService orchestrationService;

    private ClaimId validClaimId;

    @BeforeEach
    void setUp() {
        validClaimId = new ClaimId(CLAIM_ID_VALUE);
    }

    /**
     * Test Case Label: ExternalPolicySystemReturnsTimeout
     * Test Name: externalPolicySystemReturnsTimeout
     * Description: Verifies that the orchestration service correctly handles a timeout
     * response from the external policy system, ensuring graceful degradation,
     * state persistence, and appropriate exception handling without crashing the thread.
     */
    @Test
    void externalPolicySystemReturnsTimeout() {
        // Arrange: Configure mock to simulate a timeout from the external policy system.
        // In a real async orchestration, this might be a CompletableFuture.failedFuture.
        when(policySystemClient.fetchPolicyDecision(any(ClaimId.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new TimeoutException("Policy system did not respond within " + POLICY_TIMEOUT_THRESHOLD.toMillis() + "ms")));

        // Act & Assert: Expect the service to catch the timeout and transform it into
        // a domain-specific exception or handle it by updating the claim status.
        // We assert that a PolicyTimeoutException is thrown or the status is updated.
        // Here we verify the service catches the infrastructure timeout and updates status.
        
        assertDoesNotThrow(() -> orchestrationService.processDecisionFlow(validClaimId));

        // Verify side effects: Orchestration should persist the timeout state to DynamoDB via ClaimRepository.
        verify(claimRepository, times(1)).updateStatus(eq(validClaimId), eq(DecisionStatus.POLICY_TIMEOUT));
        
        // Verify interaction with external system occurred.
        verify(policySystemClient, times(1)).fetchPolicyDecision(validClaimId);
        
        // NFR: Structured Logging check (conceptual verification of method call)
        // In a full integration test with logging capture, we would verify a warning log was emitted.
    }
}
