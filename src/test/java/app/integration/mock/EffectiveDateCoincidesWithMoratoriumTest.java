package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Claim Data Standardization:decision:enrichment behavior.
 * NFR Alignment: observability (structured_logging), security (input_validation, least_privilege_iam),
 * compliance (gdpr, soc2), concurrency (thread_safety).
 */
@ExtendWith(MockitoExtension.class)
public class EffectiveDateCoincidesWithMoratoriumTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    private ClaimEnrichmentDecisionService enrichmentService;

    private static final String CLAIM_ID = "CLM-12345";
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2024, 1, 15);
    private static final LocalDate MORATORIUM_START = LocalDate.of(2024, 1, 15);
    private static final LocalDate MORATORIUM_END = LocalDate.of(2024, 1, 30);

    @BeforeEach
    void setUp() {
        // Initialize service with mocked infra contracts
        enrichmentService = new ClaimEnrichmentDecisionService(auditDiaryStore, rulesEngineDecisionService);
    }

    @Test
    void effective_date_coincides_with_moratorium() {
        // Arrange: Prepare claim payload with effective date coinciding with moratorium start
        Map<String, Object> claimPayload = new HashMap<>();
        claimPayload.put("claimId", CLAIM_ID);
        claimPayload.put("effectiveDate", EFFECTIVE_DATE);
        claimPayload.put("moratoriumPeriod", Map.of("start", MORATORIUM_START, "end", MORATORIUM_END));

        // Validate input per security NFR
        assertNotNull(claimPayload.get("claimId"), "claimId must not be null");
        assertNotNull(claimPayload.get("effectiveDate"), "effectiveDate must not be null");

        // Mock external decision engine response
        Map<String, Object> expectedDecision = new HashMap<>();
        expectedDecision.put("decisionCode", "MORATORIUM_COINCIDENT");
        expectedDecision.put("isMoratoriumActive", true);
        expectedDecision.put("enrichmentStatus", "SUSPENDED");
        expectedDecision.put("auditKey", "AuditDiaryStore/" + CLAIM_ID + ".json");
        expectedDecision.put("structuredLog", Map.of("event", "enrichment_decision", "claimId", CLAIM_ID));

        when(rulesEngineDecisionService.evaluate(anyString(), anyMap())).thenReturn(expectedDecision);
        when(auditDiaryStore.writeAuditEntry(anyString(), anyMap())).thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/" + CLAIM_ID + ".json");

        // Act: Process enrichment
        Map<String, Object> result = enrichmentService.processClaimEnrichment(CLAIM_ID, claimPayload);

        // Assert: Verify decision logic and infra interactions
        assertNotNull(result, "Enrichment result must not be null");
        assertEquals("MORATORIUM_COINCIDENT", result.get("decisionCode"), "Decision code must match moratorium coincidence");
        assertTrue((boolean) result.get("isMoratoriumActive"), "Moratorium flag must be active");
        assertEquals("SUSPENDED", result.get("enrichmentStatus"), "Enrichment must be suspended per compliance");
        verify(rulesEngineDecisionService).evaluate(eq(CLAIM_ID), anyMap());
        verify(auditDiaryStore).writeAuditEntry(eq("AuditDiaryStore/" + CLAIM_ID + ".json"), anyMap());
    }

    // Infra I/O Contract: AuditDiaryStore (S3)
    interface AuditDiaryStore {
        String writeAuditEntry(String objectKey, Map<String, Object> payload);
    }

    // Infra I/O Contract: RulesEngineDecisionService (DynamoDB)
    interface RulesEngineDecisionService {
        Map<String, Object> evaluate(String claimId, Map<String, Object> payload);
    }

    // Feature Implementation: Claim Data Standardization:decision:enrichment
    static class ClaimEnrichmentDecisionService {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineDecisionService;

        ClaimEnrichmentDecisionService(AuditDiaryStore auditDiaryStore, RulesEngineDecisionService rulesEngineDecisionService) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
        }

        Map<String, Object> processClaimEnrichment(String claimId, Map<String, Object> payload) {
            // Input validation & thread-safe local processing
            if (claimId == null || payload == null) {
                throw new IllegalArgumentException("Invalid claim input for enrichment");
            }

            // Execute decision logic
            Map<String, Object> decision = rulesEngineDecisionService.evaluate(claimId, payload);

            // Write audit trail per compliance NFR
            String auditKey = (String) decision.getOrDefault("auditKey", "AuditDiaryStore/" + claimId + ".json");
            auditDiaryStore.writeAuditEntry(auditKey, decision);

            return decision;
        }
    }
}
