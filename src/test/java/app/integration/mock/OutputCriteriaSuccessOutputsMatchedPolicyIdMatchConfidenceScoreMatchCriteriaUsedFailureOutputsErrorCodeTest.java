package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Initiation & Routing:orchestration:decision.
 * Validates output criteria, status updates, emitted events, and user-visible outputs.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionOrchestrationTest {

    @Mock
    private RedisCacheService cacheService;
    @Mock
    private DynamoDbService claimsDbService;
    @Mock
    private SesEmailService communicationService;
    @Mock
    private EventBridgePublisher eventPublisher;

    private DecisionOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new DecisionOrchestrationService(
                cacheService, claimsDbService, communicationService, eventPublisher
        );
    }

    @Test
    void testSuccessOutputsMatchedPolicyIdMatchConfidenceScoreMatchCriteriaUsed() {
        String claimId = "claim-123";
        Map<String, Object> payload = Map.of("claimId", claimId, "type", "auto");
        Map<String, Object> cachedPolicy = Map.of(
                "policyId", "POL-001",
                "confidenceScore", 0.95,
                "criteriaUsed", "RULE_A"
        );

        when(claimsDbService.getItem(anyString(), eq("pk"), eq(claimId))).thenReturn(Map.of("status", "INITIATED"));
        when(cacheService.get(anyString())).thenReturn(Optional.of(new String(cachedPolicy.toString().getBytes())));

        Map<String, Object> result = orchestrationService.decideAndRoute(payload);

        assertNotNull(result);
        assertTrue(result.containsKey("matched_policy_id"));
        assertTrue(result.containsKey("match_confidence_score"));
        assertTrue(result.containsKey("match_criteria_used"));
        assertEquals("POL-001", result.get("matched_policy_id"));
        assertEquals(0.95, result.get("match_confidence_score"));
        assertEquals("RULE_A", result.get("match_criteria_used"));
    }

    @Test
    void testFailureOutputsErrorCodeValidationMessages() {
        Map<String, Object> invalidPayload = Map.of("claimId", "", "type", "invalid");

        Map<String, Object> result = orchestrationService.decideAndRoute(invalidPayload);

        assertNotNull(result);
        assertTrue(result.containsKey("error_code"));
        assertTrue(result.containsKey("validation_messages"));
        assertEquals("VALIDATION_FAILED", result.get("error_code"));
        List<String> messages = (List<String>) result.get("validation_messages");
        assertNotNull(messages);
        assertFalse(messages.isEmpty());
    }

    @Test
    void testStatusUpdatesFnolStatusMatchedMultipleMatchUnmatched() {
        when(claimsDbService.getItem(anyString(), anyString(), anyString())).thenReturn(Map.of("status", "INITIATED"));

        // MATCHED
        when(cacheService.get(anyString())).thenReturn(Optional.of(new String(Map.of("policyId", "POL-001", "confidenceScore", 0.98).toString().getBytes())));
        Map<String, Object> matchedResult = orchestrationService.decideAndRoute(Map.of("claimId", "c1"));
        assertEquals("MATCHED", matchedResult.get("FNOL_STATUS"));

        // MULTIPLE_MATCH
        when(cacheService.get(anyString())).thenReturn(Optional.of(new String(Map.of("policyId", "POL-002", "confidenceScore", 0.60).toString().getBytes())));
        Map<String, Object> multipleResult = orchestrationService.decideAndRoute(Map.of("claimId", "c2"));
        assertEquals("MULTIPLE_MATCH", multipleResult.get("FNOL_STATUS"));

        // UNMATCHED
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        Map<String, Object> unmatchedResult = orchestrationService.decideAndRoute(Map.of("claimId", "c3"));
        assertEquals("UNMATCHED", unmatchedResult.get("FNOL_STATUS"));
    }

    @Test
    void testEmittedEventsPolicyMatchedPolicyMultipleMatchesPolicyUnmatched() {
        when(claimsDbService.getItem(anyString(), anyString(), anyString())).thenReturn(Map.of("status", "INITIATED"));

        // policy.matched
        when(cacheService.get(anyString())).thenReturn(Optional.of(new String(Map.of("policyId", "POL-001", "confidenceScore", 0.95).toString().getBytes())));
        orchestrationService.decideAndRoute(Map.of("claimId", "c1"));

        // policy.multiple_matches
        when(cacheService.get(anyString())).thenReturn(Optional.of(new String(Map.of("policyId", "POL-002", "confidenceScore", 0.55).toString().getBytes())));
        orchestrationService.decideAndRoute(Map.of("claimId", "c2"));

        // policy.unmatched
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        orchestrationService.decideAndRoute(Map.of("claimId", "c3"));

        verify(eventPublisher, times(1)).publish(eq("policy.matched"), anyString());
        verify(eventPublisher, times(1)).publish(eq("policy.multiple_matches"), anyString());
        verify(eventPublisher, times(1)).publish(eq("policy.unmatched"), anyString());
    }

    @Test
    void testUserVisibleOutputsPolicyDetailsSummaryRequestToResolveMultipleMatchesNotificationUnderManualReview() {
        when(claimsDbService.getItem(anyString(), anyString(), anyString())).thenReturn(Map.of("status", "INITIATED"));

        // Policy details summary.
        when(cacheService.get(anyString())).thenReturn(Optional.of(new String(Map.of("policyId", "POL-001", "confidenceScore", 0.95).toString().getBytes())));
        Map<String, Object> matchedResult = orchestrationService.decideAndRoute(Map.of("claimId", "c1"));
        assertEquals("Policy details summary.", matchedResult.get("user_visible_output"));

        // Request to resolve multiple matches.
        when(cacheService.get(anyString())).thenReturn(Optional.of(new String(Map.of("policyId", "POL-002", "confidenceScore", 0.55).toString().getBytes())));
        Map<String, Object> multipleResult = orchestrationService.decideAndRoute(Map.of("claimId", "c2"));
        assertEquals("Request to resolve multiple matches.", multipleResult.get("user_visible_output"));

        // Notification that claim is under manual review.
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        Map<String, Object> unmatchedResult = orchestrationService.decideAndRoute(Map.of("claimId", "c3"));
        assertEquals("Notification that claim is under manual review.", unmatchedResult.get("user_visible_output"));
    }
}

// --- Mock Infrastructure Interfaces ---
interface RedisCacheService {
    Optional<String> get(String key);
}

interface DynamoDbService {
    Map<String, Object> getItem(String tableName, String partitionKey, String key);
}

interface SesEmailService {
    String sendEmail(String from, List<String> to, String subject, String body);
}

interface EventBridgePublisher {
    void publish(String eventSource, String payload);
}

// --- Orchestration Service (Simplified Implementation for Testing) ---
class DecisionOrchestrationService {
    private final RedisCacheService cacheService;
    private final DynamoDbService claimsDbService;
    private final SesEmailService communicationService;
    private final EventBridgePublisher eventPublisher;

    DecisionOrchestrationService(RedisCacheService cacheService, DynamoDbService claimsDbService,
                                 SesEmailService communicationService, EventBridgePublisher eventPublisher) {
        this.cacheService = cacheService;
        this.claimsDbService = claimsDbService;
        this.communicationService = communicationService;
        this.eventPublisher = eventPublisher;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> decideAndRoute(Map<String, Object> payload) {
        String claimId = (String) payload.get("claimId");
        if (claimId == null || claimId.isBlank()) {
            Map<String, Object> errorOutput = new HashMap<>();
            errorOutput.put("error_code", "VALIDATION_FAILED");
            errorOutput.put("validation_messages", List.of("claimId is required and must be non-empty"));
            errorOutput.put("FNOL_STATUS", "UNMATCHED");
            errorOutput.put("user_visible_output", "Notification that claim is under manual review.");
            return errorOutput;
        }

        Map<String, Object> cachedPolicy = cacheService.get("Cache & Reference Data:cache:" + claimId)
                .map(val -> {
                    try {
                        return (Map<String, Object>) new java.util.Base64.Decoder() {}.decode(val);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);

        if (cachedPolicy == null) {
            Map<String, Object> unmatched = new HashMap<>();
            unmatched.put("error_code", "NO_MATCH_FOUND");
            unmatched.put("validation_messages", List.of("No matching policy found in reference data"));
            unmatched.put("FNOL_STATUS", "UNMATCHED");
            unmatched.put("user_visible_output", "Notification that claim is under manual review.");
            eventPublisher.publish("policy.unmatched", claimId);
            return unmatched;
        }

        Double confidence = (Double) cachedPolicy.get("confidenceScore");
        if (confidence != null && confidence >= 0.8) {
            Map<String, Object> matched = new HashMap<>();
            matched.put("matched_policy_id", cachedPolicy.get("policyId"));
            matched.put("match_confidence_score", confidence);
            matched.put("match_criteria_used", cachedPolicy.get("criteriaUsed"));
            matched.put("FNOL_STATUS", "MATCHED");
            matched.put("user_visible_output", "Policy details summary.");
            eventPublisher.publish("policy.matched", claimId);
            return matched;
        } else {
            Map<String, Object> multiple = new HashMap<>();
            multiple.put("error_code", "AMBIGUOUS_MATCH");
            multiple.put("validation_messages", List.of("Multiple potential matches with low confidence"));
            multiple.put("FNOL_STATUS", "MULTIPLE_MATCH");
            multiple.put("user_visible_output", "Request to resolve multiple matches.");
            eventPublisher.publish("policy.multiple_matches", claimId);
            return multiple;
        }
    }
}
