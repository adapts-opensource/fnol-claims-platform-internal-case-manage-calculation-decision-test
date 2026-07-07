package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Minimal interfaces representing external I/O contracts (DynamoDB & S3)
interface RulesEngineDecisionService {
    Map<String, Object> fetchRules(String productId);
}

interface AuditDiaryStore {
    void logAuditEvent(String entityId, String eventType, Map<String, Object> payload);
}

interface ClaimEnrichmentService {
    Map<String, Object> enrich(String claimId, Map<String, Object> payload);
}

class ClaimDataEnrichmentProcessor {
    private final RulesEngineDecisionService rulesEngineService;
    private final AuditDiaryStore auditDiaryStore;
    private final ClaimEnrichmentService enrichmentService;

    ClaimDataEnrichmentProcessor(RulesEngineDecisionService rulesEngineService, AuditDiaryStore auditDiaryStore, ClaimEnrichmentService enrichmentService) {
        this.rulesEngineService = rulesEngineService;
        this.auditDiaryStore = auditDiaryStore;
        this.enrichmentService = enrichmentService;
    }

    Map<String, Object> processClaimEnrichment(String claimId, String productId, Map<String, Object> payload) {
        // NFR: structured_logging, observability
        Map<String, Object> rules = rulesEngineService.fetchRules(productId);
        payload.put("validationRuleVersion", rules.get("version"));
        payload.put("isStandardized", true);
        Map<String, Object> enriched = enrichmentService.enrich(claimId, payload);
        auditDiaryStore.logAuditEvent(claimId, "ENRICHMENT_SUCCESS", enriched);
        return enriched;
    }
}

@ExtendWith(MockitoExtension.class)
class AppliesWhenAdminModifiesProductFormValidationRulesTest {

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private ClaimEnrichmentService enrichmentService;

    private ClaimDataEnrichmentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ClaimDataEnrichmentProcessor(rulesEngineService, auditDiaryStore, enrichmentService);
    }

    @Test
    void applies_when_admin_modifies_product_form_validation_rules() {
        // Arrange: Admin updates product validation rules in DynamoDB-backed rules engine
        String claimId = "claim-std-001";
        String productId = "prod-auto-2024";
        Map<String, Object> originalPayload = Map.of("id", claimId, "payload", Map.of("formVersion", "1.0"));
        Map<String, Object> updatedRules = Map.of("ruleId", "val-rules-v2", "version", "2.0");
        Map<String, Object> expectedEnrichedPayload = Map.of("id", claimId, "payload", Map.of("formVersion", "2.0", "isStandardized", true));

        when(rulesEngineService.fetchRules(productId)).thenReturn(updatedRules);
        when(enrichmentService.enrich(eq(claimId), anyMap())).thenReturn(expectedEnrichedPayload);

        // Act: Trigger enrichment pipeline
        Map<String, Object> result = processor.processClaimEnrichment(claimId, productId, originalPayload);

        // Assert: Verify enrichment applies new validation rules
        assertNotNull(result);
        assertEquals("2.0", result.get("validationRuleVersion"));
        assertTrue((Boolean) result.get("isStandardized"));

        // Verify external I/O interactions (mocked, no live AWS calls)
        verify(rulesEngineService).fetchRules(productId);
        verify(enrichmentService).enrich(eq(claimId), anyMap());
        verify(auditDiaryStore).logAuditEvent(eq(claimId), eq("ENRICHMENT_SUCCESS"), anyMap());

        // NFR: compliance (GDPR/SOC2) - verify no PII leakage in enriched payload
        assertFalse(result.containsKey("ssn"), "PII fields must not be exposed in enrichment output");
    }
}
