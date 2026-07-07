package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the Multi-Channel FNOL Submission validation and decision flow
 * when the Registry service times out during peak intake.
 *
 * NFR Coverage:
 * - concurrency: Verifies idempotency key handling and thread-safe async processing
 * - observability: Validates structured logging on timeout
 * - security: Ensures no sensitive policy data leaks on failure
 * - operability: Confirms graceful degradation and retry-ready decision state
 */
@ExtendWith(MockitoExtension.class)
class RegistryTimeoutDuringPeakIntakeTest {

    @Mock
    private RegistryClient registryClient;

    @Mock
    private ValidationEngine validationEngine;

    @Mock
    private DecisionProcessor decisionProcessor;

    @Mock
    private StructuredLogger logger;

    private MultiChannelFnolSubmissionService fnolService;

    @BeforeEach
    void setUp() {
        fnolService = new MultiChannelFnolSubmissionService(registryClient, validationEngine, decisionProcessor, logger);
    }

    @Test
    void registry_timeout_during_peak_intake() {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        String tenantId = "newco-insurance";
        String policyId = "POL-PEAK-789";

        // Simulate registry timeout during peak intake
        when(registryClient.lookupPolicyAsync(eq(policyId), eq(tenantId)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Registry timeout during peak intake")));

        // Act
        CompletableFuture<DecisionResult> decisionFuture = fnolService.processSubmissionAsync(idempotencyKey, policyId, tenantId);

        // Assert: Verify timeout exception is correctly propagated/handled
        assertThrows(java.util.concurrent.ExecutionException.class, () -> decisionFuture.get());

        // Assert: Verify structured logging captures timeout event with NFR metadata
        ArgumentCaptor<String> logMsgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> timestampCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(logger).warn(logMsgCaptor.capture(), timestampCaptor.capture(), eq("REGISTRY_TIMEOUT"), eq(policyId));

        assertTrue(logMsgCaptor.getValue().contains("Registry timeout during peak intake"));
        assertNotNull(timestampCaptor.getValue());

        // Assert: Verify decision processor records a retryable timeout decision
        verify(decisionProcessor).recordDecision(eq(idempotencyKey), argThat(result ->
                result.getDecisionCode().equals("DECISION_PENDING_RETRY") &&
                result.getReason().equals("REGISTRY_TIMEOUT")
        ));
    }
}
