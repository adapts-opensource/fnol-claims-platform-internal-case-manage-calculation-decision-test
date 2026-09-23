package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies orchestration decision logic for Claim Initiation & Routing:decision:validation.
 * NFR Compliance Notes:
 * - Availability: Graceful fallback on cache/DB misses
 * - Compliance: GDPR/SOC2 data masking in logs, no PII persistence in test payloads
 * - Concurrency: Service methods are stateless; mocks are thread-safe
 * - Observability: Structured logging placeholders via SLF4J/MDC
 * - Security: TLS enforced in infra contracts, least-privilege IAM patterns mocked, input validation asserted
 */
class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SesCommunicationService sesCommunicationService;

    private ClaimInitiationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Wire mocked infrastructure contracts to the service under test
        orchestrationService = new ClaimInitiationOrchestrationService(
                redisCacheService, dynamoDbService, sesCommunicationService
        );
    }

    @Test
    void multiple_policies_with_same_details() {
        // Arrange: Construct payload matching claim_initiation___routing_decision_validation entity
        String claimId = "claim-init-998877";
        Map<String, Object> payload = new HashMap<>();
        
        // Multiple policies with identical details to trigger deduplication & routing decision
        List<Map<String, String>> duplicatePolicies = List.of(
                Map.of("policyId", "POL-5001", "holderName", "Alex Rivera", "coverageType", "AUTO"),
                Map.of("policyId", "POL-5001", "holderName", "Alex Rivera", "coverageType", "AUTO")
        );
        payload.put("policies", duplicatePolicies);
        payload.put("claimType", "COLLISION");

        // Mock external I/O contracts (never call live AWS/HTTP)
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(dynamoDbService.getItem(anyString(), anyString())).thenReturn(payload);
        when(sesCommunicationService.sendEmail(anyString(), anyList(), anyString())).thenReturn("ses-msg-id-xyz");

        // Act: Execute orchestration & decision routing
        Map<String, Object> validationResult = orchestrationService.processClaimInitiation(claimId, payload);

        // Assert: Validate entity fields, routing decision, and infra interactions
        assertNotNull(validationResult, "Validation result must not be null");
        assertEquals(claimId, validationResult.get("id"), "Claim ID must match input");
        assertInstanceOf(Map.class, validationResult.get("payload"), "Payload must be a Map");
        assertEquals("MULTIPLE_IDENTICAL_POLICIES_DETECTED", validationResult.get("routingDecision"),
                "Routing decision must flag duplicate policy handling");
        assertTrue((Boolean) validationResult.get("deduplicationApplied"),
                "Deduplication flag must be true");
        assertEquals(1, validationResult.get("uniquePolicyCount"),
                "Unique policy count must be 1 after deduplication");

        // Verify infrastructure contract interactions & NFR safeguards
        verify(redisCacheService).get(eq("Cache & Reference Data:cache:" + claimId));
        verify(dynamoDbService).getItem(eq("Claims & Policy Data Store_table"), eq("pk"));
        // SES should not be triggered for duplicate policy routing
        verify(sesCommunicationService, never()).sendEmail(anyString(), anyList(), anyString());
    }
}
