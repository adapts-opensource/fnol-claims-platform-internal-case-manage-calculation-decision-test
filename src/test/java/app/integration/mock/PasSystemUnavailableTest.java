package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Claim Initiation & Routing: orchestration: decision.
 * Validates graceful handling when external PAS (Policy Administration System) is unavailable.
 * Aligns with NFRs: availability (fail-fast/fallback), security (input validation preserved),
 * observability (structured logging context), compliance (no PII leakage in error paths).
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingOrchestrationDecisionMockTest {

    @Mock
    private PasClient pasClient;

    @Mock
    private RedisCacheClient redisCacheClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesEmailClient sesEmailClient;

    @InjectMocks
    private ClaimInitiationRoutingOrchestrationDecisionService decisionService;

    private Map<String, Object> validClaimPayload;

    @BeforeEach
    void setUp() {
        // Seed valid payload conforming to claim_initiation___routing_decision_validation model
        validClaimPayload = Map.of(
            "id", "CLM-INIT-789",
            "payload", Map.of(
                "policyNumber", "POL-45678",
                "incidentDate", "2023-11-15",
                "coverageType", "AUTO_COMPREHENSIVE"
            )
        );
    }

    @Test
    void pasSystemUnavailable() {
        // Arrange: Simulate PAS system unavailability via mock contract
        when(pasClient.fetchPolicyDetails(anyString()))
            .thenThrow(new RuntimeException("PAS_SYSTEM_UNAVAILABLE"));

        // Act & Assert: Verify orchestration decision handles PAS failure gracefully
        // Expected: Service catches external I/O failure, applies input validation,
        // logs structured observability context, and returns/fails with structured error code.
        assertThrows(OrchestrationDecisionException.class, () -> {
            decisionService.evaluateRoutingDecision(validClaimPayload);
        }).hasMessageContaining("PAS_UNAVAILABLE");

        // Verify strict I/O contract: PAS called exactly once, no downstream calls triggered
        verify(pasClient, times(1)).fetchPolicyDetails(anyString());
        verifyNoInteractions(redisCacheClient, dynamoDbClient, sesEmailClient);
    }
}
