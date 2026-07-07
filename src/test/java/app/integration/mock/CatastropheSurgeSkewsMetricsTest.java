package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class CatastropheSurgeSkewsMetricsTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    private ClaimEnrichmentProcessor enrichmentProcessor;
    private String testClaimId;
    private Map<String, Object> inputPayload;

    @BeforeEach
    void setUp() {
        testClaimId = "claim-catastrophe-001";
        inputPayload = new HashMap<>();
        inputPayload.put("id", testClaimId);
        inputPayload.put("event_type", "CATASTROPHE_SURGE");
        inputPayload.put("baseline_metric_value", 100.0);
        inputPayload.put("surge_intensity_factor", 0.85);

        enrichmentProcessor = new ClaimEnrichmentProcessor(auditDiaryStore, rulesEngineDecisionService);
    }

    @Test
    void catastrophe_surge_skews_metrics() {
        // Arrange
        String expectedUri = "s3://AuditDiaryStore-bucket/AuditDiaryStore/" + testClaimId + ".json";
        Map<String, Object> expectedDecision = new HashMap<>();
        expectedDecision.put("decision_code", "SURGE_ACCEPTED");

        when(auditDiaryStore.write("AuditDiaryStore-bucket", "AuditDiaryStore/" + testClaimId + ".json"))
                .thenReturn(expectedUri);
        when(rulesEngineDecisionService.fetchDecision("RulesEngineDecisionService_table", "pk_" + testClaimId))
                .thenReturn(expectedDecision);

        // Act
        Map<String, Object> enrichedPayload = enrichmentProcessor.processClaimData(testClaimId, inputPayload);

        // Assert
        assertNotNull(enrichedPayload);
        assertEquals(185.0, enrichedPayload.get("enriched_metric_value"));
        assertEquals("SURGE_ADJUSTED", enrichedPayload.get("enrichment_status"));
        assertEquals(expectedUri, enrichedPayload.get("audit_uri"));
        assertEquals("SURGE_ACCEPTED", enrichedPayload.get("decision_code"));

        // Verify infra I/O contracts were invoked exactly once
        verify(auditDiaryStore).write("AuditDiaryStore-bucket", "AuditDiaryStore/" + testClaimId + ".json");
        verify(rulesEngineDecisionService).fetchDecision("RulesEngineDecisionService_table", "pk_" + testClaimId);
    }

    // Simplified system-under-test implementation for mock validation
    static class ClaimEnrichmentProcessor {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineDecisionService;

        ClaimEnrichmentProcessor(AuditDiaryStore auditDiaryStore, RulesEngineDecisionService rulesEngineDecisionService) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
        }

        Map<String, Object> processClaimData(String claimId, Map<String, Object> payload) {
            Map<String, Object> enriched = new HashMap<>(payload);
            double baseline = (double) payload.get("baseline_metric_value");
            double surgeFactor = (double) payload.get("surge_intensity_factor");
            
            // Simulate catastrophe surge skewing the baseline metric
            enriched.put("enriched_metric_value", baseline + (baseline * surgeFactor));
            enriched.put("enrichment_status", "SURGE_ADJUSTED");

            // Write audit diary to S3 (mocked)
            String uri = auditDiaryStore.write("AuditDiaryStore-bucket", "AuditDiaryStore/" + claimId + ".json");
            enriched.put("audit_uri", uri);

            // Fetch decision from DynamoDB (mocked)
            Map<String, Object> decision = rulesEngineDecisionService.fetchDecision("RulesEngineDecisionService_table", "pk_" + claimId);
            enriched.put("decision_code", decision.get("decision_code"));

            return enriched;
        }
    }

    interface AuditDiaryStore {
        String write(String bucketName, String objectKey);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> fetchDecision(String tableName, String partitionKey);
    }
}
