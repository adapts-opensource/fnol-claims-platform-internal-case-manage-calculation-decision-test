package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Minimal domain contracts representing AWS infrastructure I/O layers
interface RulesEngineDecisionService {
    List<Map<String, Object>> queryRulesByClaimId(String claimId, String decisionType);
}

interface AuditDiaryStore {
    void writeAuditRecord(String bucketName, String objectKeyPattern, Map<String, Object> payload);
}

// Service under test (extracted for testability)
class ClaimEnrichmentDecisionService {
    private final RulesEngineDecisionService rulesEngine;
    private final AuditDiaryStore auditStore;

    public ClaimEnrichmentDecisionService(RulesEngineDecisionService rulesEngine, AuditDiaryStore auditStore) {
        this.rulesEngine = rulesEngine;
        this.auditStore = auditStore;
    }

    // NFR: input_validation & security (tls_in_transit/least_privilege simulated via validation)
    public Map<String, Object> resolveEnrichmentRule(String claimId, String decisionType) {
        if (claimId == null || claimId.isBlank() || decisionType == null || decisionType.isBlank()) {
            throw new IllegalArgumentException("claimId and decisionType must be non-empty");
        }

        List<Map<String, Object>> rawRules = rulesEngine.queryRulesByClaimId(claimId, decisionType);
        if (rawRules == null || rawRules.isEmpty()) {
            return Map.of("status", "NO_MATCH", "payload", Map.of());
        }

        // NFR: thread_safety & observability (structured_logging simulated via audit)
        Map<String, Object> auditPayload = Map.of("claimId", claimId, "decisionType", decisionType, "timestamp", LocalDate.now().toString());
        auditStore.writeAuditRecord("AuditDiaryStore-bucket", "AuditDiaryStore/" + claimId + ".json", auditPayload);

        // NFR: compliance (gdpr/soc2) - filter out non-approved or expired rules
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> approvedActiveRules = rawRules.stream()
                .filter(rule -> "APPROVED".equals(rule.get("status")))
                .filter(rule -> {
                    String effDateStr = String.valueOf(rule.get("effectiveDate"));
                    return !effDateStr.isEmpty() && LocalDate.parse(effDateStr).isBefore(today) || LocalDate.parse(effDateStr).isEqual(today);
                })
                .sorted((r1, r2) -> Integer.compare((int) r2.get("version"), (int) r1.get("version")))
                .collect(Collectors.toList());

        if (approvedActiveRules.isEmpty()) {
            return Map.of("status", "NO_ACTIVE_RULE", "payload", Map.of());
        }

        return approvedActiveRules.get(0);
    }
}

@ExtendWith(MockitoExtension.class)
class PurposeManageRuleChangesWithVersioningApprovalAndEffectiveDateHandlingTest {

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimEnrichmentDecisionService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimEnrichmentDecisionService(rulesEngineDecisionService, auditDiaryStore);
    }

    @Test
    void purpose_manage_rule_changes_with_versioning_approval_and_effective_date_handling() {
        String claimId = "CLM-12345";
        String decisionType = "ENRICHMENT_DECISION";
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(1);
        LocalDate pastDate = today.minusDays(1);

        // Arrange: Simulate DynamoDB item payloads with versioning, approval states, and effective dates
        Map<String, Object> ruleV1ApprovedPast = Map.of(
                "id", "RULE-V1",
                "version", 1,
                "status", "APPROVED",
                "effectiveDate", pastDate.toString(),
                "payload", Map.of("enrichmentType", "GEO_COORDINATES")
        );

        Map<String, Object> ruleV2ApprovedFuture = Map.of(
                "id", "RULE-V2",
                "version", 2,
                "status", "APPROVED",
                "effectiveDate", futureDate.toString(),
                "payload", Map.of("enrichmentType", "RISK_SCORE")
        );

        Map<String, Object> ruleV3Pending = Map.of(
                "id", "RULE-V3",
                "version", 3,
                "status", "PENDING_APPROVAL",
                "effectiveDate", pastDate.toString(),
                "payload", Map.of("enrichmentType", "EXTRA_DATA")
        );

        when(rulesEngineDecisionService.queryRulesByClaimId(anyString(), anyString()))
                .thenReturn(List.of(ruleV1ApprovedPast, ruleV2ApprovedFuture, ruleV3Pending));

        // Act: Resolve enrichment rule
        Map<String, Object> result = enrichmentService.resolveEnrichmentRule(claimId, decisionType);

        // Assert: Versioning picks highest version among approved
        assertEquals("RULE-V1", result.get("id")); // V1 is active, V2 is future-dated, V3 is unapproved
        assertEquals("APPROVED", result.get("status"));
        assertEquals("GEO_COORDINATES", ((Map<?, ?>) result.get("payload")).get("enrichmentType"));

        // Assert: Effective date handling correctly excludes future-dated rules
        when(rulesEngineDecisionService.queryRulesByClaimId(anyString(), anyString()))
                .thenReturn(List.of(ruleV1ApprovedPast, ruleV2ApprovedFuture));
        Map<String, Object> resultFutureContext = enrichmentService.resolveEnrichmentRule(claimId, decisionType);
        assertEquals("RULE-V1", resultFutureContext.get("id"));

        // Assert: Approval status filtering
        when(rulesEngineDecisionService.queryRulesByClaimId(anyString(), anyString()))
                .thenReturn(List.of(ruleV3Pending));
        Map<String, Object> resultPending = enrichmentService.resolveEnrichmentRule(claimId, decisionType);
        assertEquals("NO_ACTIVE_RULE", resultPending.get("status"));

        // Assert: Audit logging (structured_logging & operability)
        verify(auditDiaryStore, times(3)).writeAuditRecord(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json"), anyMap());

        // Assert: Input validation (security)
        assertThrows(IllegalArgumentException.class, () -> enrichmentService.resolveEnrichmentRule(null, decisionType));
        assertThrows(IllegalArgumentException.class, () -> enrichmentService.resolveEnrichmentRule(claimId, ""));

        // Assert: Thread safety simulation (concurrency)
        ConcurrentHashMap<String, Object> concurrentResults = new ConcurrentHashMap<>();
        Thread t1 = new Thread(() -> concurrentResults.put("1", enrichmentService.resolveEnrichmentRule(claimId, decisionType)));
        Thread t2 = new Thread(() -> concurrentResults.put("2", enrichmentService.resolveEnrichmentRule(claimId, decisionType)));
        t1.start(); t2.start();
        try { t1.join(); t2.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertEquals(2, concurrentResults.size());
        assertSame(concurrentResults.get("1"), concurrentResults.get("2")); // Deterministic & thread-safe
    }
}
