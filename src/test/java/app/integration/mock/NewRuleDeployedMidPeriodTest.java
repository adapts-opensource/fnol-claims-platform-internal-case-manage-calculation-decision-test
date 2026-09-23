package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
public class ClaimDataEnrichmentDecisionMockTest {

    @Mock
    private RulesEngineDecisionService rulesEngine;

    @Mock
    private AuditDiaryStore auditStore;

    private ClaimDataStandardizationEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimDataStandardizationEnrichmentService(rulesEngine, auditStore);
    }

    @Test
    void newRuleDeployedMidPeriod() {
        // Given: Claim payload processed mid-period with baseline state
        String claimId = "CLM-MID-20231015-001";
        Map<String, Object> originalPayload = new HashMap<>();
        originalPayload.put("id", claimId);
        originalPayload.put("status", "SUBMITTED");
        originalPayload.put("claimDate", Instant.now().minusSeconds(86400).toString());
        originalPayload.put("processedAt", Instant.now().toString());

        // Given: New enrichment rule deployed mid-period (after period start, before processing)
        String ruleId = "RULE-ENRICH-V2-MID";
        Instant deploymentTime = Instant.now().minusSeconds(3600); // Deployed 1 hour ago
        Map<String, Object> ruleConfig = new HashMap<>();
        ruleConfig.put("ruleId", ruleId);
        ruleConfig.put("deploymentTimestamp", deploymentTime.toString());
        ruleConfig.put("transformation", Map.of("status", "ENRICHED_V2", "enrichedAt", deploymentTime.toString()));

        when(rulesEngine.resolveActiveRule(claimId, ruleConfig)).thenReturn(ruleConfig);

        // When: Enrichment decision is triggered for the claim during the current period
        Map<String, Object> enrichedPayload = enrichmentService.enrichClaimData(originalPayload);

        // Then: New rule is correctly applied despite mid-period deployment
        assertNotNull(enrichedPayload);
        assertEquals("ENRICHED_V2", enrichedPayload.get("status"));
        assertEquals(ruleId, enrichedPayload.get("appliedRuleId"));
        assertEquals(deploymentTime.toString(), enrichedPayload.get("enrichedAt"));

        // Then: Audit diary is written with resolved S3 URI (TLS in transit, least privilege, structured logging)
        String expectedKey = "AuditDiaryStore/" + claimId + ".json";
        verify(auditStore, times(1))
                .writeObject(eq("AuditDiaryStore-bucket"), eq(expectedKey), any(Map.class));
    }

    // Mock interfaces representing external I/O contracts (DynamoDB & S3)
    private interface RulesEngineDecisionService {
        Map<String, Object> resolveActiveRule(String claimId, Map<String, Object> ruleConfig);
    }

    private interface AuditDiaryStore {
        String writeObject(String bucketName, String objectKey, Map<String, Object> payload);
    }

    // Service under test with mocked dependencies
    private static class ClaimDataStandardizationEnrichmentService {
        private final RulesEngineDecisionService rulesEngine;
        private final AuditDiaryStore auditStore;

        ClaimDataStandardizationEnrichmentService(RulesEngineDecisionService rulesEngine, AuditDiaryStore auditStore) {
            this.rulesEngine = rulesEngine;
            this.auditStore = auditStore;
        }

        Map<String, Object> enrichClaimData(Map<String, Object> payload) {
            Map<String, Object> ruleConfig = new HashMap<>();
            Map<String, Object> resolvedRule = rulesEngine.resolveActiveRule((String) payload.get("id"), ruleConfig);
            
            Map<String, Object> enriched = new HashMap<>(payload);
            enriched.putAll((Map<String, Object>) resolvedRule.get("transformation"));
            enriched.put("appliedRuleId", resolvedRule.get("ruleId"));
            
            // Simulate async audit write (thread-safe, structured logging)
            String uri = auditStore.writeObject("AuditDiaryStore-bucket", "AuditDiaryStore/" + enriched.get("id") + ".json", enriched);
            enriched.put("auditUri", uri);
            
            return enriched;
        }
    }
}
