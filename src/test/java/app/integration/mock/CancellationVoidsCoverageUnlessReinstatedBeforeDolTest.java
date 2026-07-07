package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationMockTest {

    @Mock
    private CacheService mockCacheService;
    @Mock
    private PolicyDataStore mockPolicyDataStore;
    @Mock
    private NotificationService mockNotificationService;

    private ClaimDecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimDecisionOrchestrator(mockPolicyDataStore, mockCacheService, mockNotificationService);
    }

    @Test
    void cancellation_voids_coverage_unless_reinstated_before_dol() {
        // Given: Policy cancelled, reinstated AFTER Date of Loss (DOL)
        String policyId = "POL-12345";
        LocalDate cancellationDate = LocalDate.of(2023, 10, 1);
        LocalDate reinstatementDate = LocalDate.of(2023, 10, 15); // After DOL
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 10);
        String claimId = "CLM-67890";

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("policyId", policyId);
        payload.put("cancellationDate", cancellationDate);
        payload.put("reinstatementDate", reinstatementDate);
        payload.put("dateOfLoss", dateOfLoss);

        // Mock external I/O contracts (Redis & DynamoDB)
        when(mockPolicyDataStore.getItem(policyId)).thenReturn(Map.of("status", "CANCELLED"));
        when(mockCacheService.getCacheValue(anyString())).thenReturn("policy_rules_v1");

        // When
        Map<String, Object> decision = orchestrator.evaluateDecision(payload);

        // Then: Coverage is voided because reinstatement occurred after DOL
        assertEquals("VOIDED", decision.get("coverageStatus"));
        assertEquals("REJECT_CLAIM", decision.get("routingDecision"));
        assertTrue(decision.containsKey("validationReason"));
        assertNotNull(decision.get("payload"));

        // Verify infra I/O calls were executed (mocked, no live AWS calls)
        verify(mockCacheService).getCacheValue(anyString());
        verify(mockPolicyDataStore).getItem(policyId);
    }

    // Infra contract mocks (thread-safe, stateless interfaces)
    interface CacheService {
        String getCacheValue(String key);
    }

    interface PolicyDataStore {
        Map<String, Object> getItem(String partitionKey);
    }

    interface NotificationService {
        String sendAcknowledgment(Map<String, Object> request);
    }

    // Simplified orchestrator implementing the business rule with structured logging
    static class ClaimDecisionOrchestrator {
        private final PolicyDataStore policyDataStore;
        private final CacheService cacheService;
        private final NotificationService notificationService;

        ClaimDecisionOrchestrator(PolicyDataStore policyDataStore, CacheService cacheService, NotificationService notificationService) {
            this.policyDataStore = policyDataStore;
            this.cacheService = cacheService;
            this.notificationService = notificationService;
        }

        Map<String, Object> evaluateDecision(Map<String, Object> payload) {
            String policyId = (String) payload.get("policyId");
            LocalDate cancellationDate = (LocalDate) payload.get("cancellationDate");
            LocalDate reinstatementDate = (LocalDate) payload.get("reinstatementDate");
            LocalDate dateOfLoss = (LocalDate) payload.get("dateOfLoss");
            String claimId = (String) payload.get("id");

            // Validate infra I/O contracts
            Map<String, Object> policyData = policyDataStore.getItem(policyId);
            String cacheRef = cacheService.getCacheValue("Cache & Reference Data:cache:rules");

            // Business Rule: Cancellation voids coverage unless reinstated before DOL
            String coverageStatus = "ACTIVE";
            String routingDecision = "ROUTE_TO_UNDERWRITER";
            String validationReason = "Coverage valid";

            if ("CANCELLED".equals(policyData.get("status"))) {
                if (reinstatementDate != null && !reinstatementDate.isAfter(dateOfLoss)) {
                    coverageStatus = "REINSTATED";
                    routingDecision = "ROUTE_TO_CLAIMS_ADJUSTER";
                    validationReason = "Reinstated before DOL";
                } else {
                    coverageStatus = "VOIDED";
                    routingDecision = "REJECT_CLAIM";
                    validationReason = "Reinstated after DOL or not reinstated";
                }
            }

            Map<String, Object> decision = new HashMap<>();
            decision.put("id", claimId);
            decision.put("payload", payload);
            decision.put("coverageStatus", coverageStatus);
            decision.put("routingDecision", routingDecision);
            decision.put("validationReason", validationReason);
            decision.put("cacheReference", cacheRef);

            // NFR: Observability - Structured logging
            System.out.println(String.format("[STRUCTURED_LOG] claimId=%s decision=%s coverage=%s ts=%s", 
                    claimId, routingDecision, coverageStatus, java.time.Instant.now()));

            return decision;
        }
    }
}
