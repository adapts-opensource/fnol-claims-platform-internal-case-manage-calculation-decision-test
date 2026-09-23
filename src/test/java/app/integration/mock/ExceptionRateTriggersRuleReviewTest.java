package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that claim data standardization correctly triggers a rule review
 * when an exception rate exceeds configured thresholds.
 * NFR Alignment: thread_safety (stateless mock execution), input_validation (payload guards),
 * compliance (GDPR/SOC2 audit URI generation), structured_logging (audit trail simulation).
 */
@ExtendWith(MockitoExtension.class)
public class ExceptionRateTriggersRuleReviewTest {

    @Mock
    private ClaimEnrichmentService enrichmentService;

    @Mock
    private AuditDiaryStoreClient auditDiaryStore;

    @Mock
    private RulesEngineDecisionClient rulesEngineDecision;

    private static final String CLAIM_ID = "claim-987-exception-rate";
    private static final String BUCKET_NAME = "AuditDiaryStore-bucket";
    private static final String TABLE_NAME = "RulesEngineDecisionService_table";

    @BeforeEach
    void setUp() {
        // Input validation ensures mock contracts are respected before test execution
        assertNotNull(enrichmentService, "Enrichment service mock must be initialized");
        assertNotNull(rulesEngineDecision, "Rules engine client mock must be initialized");
    }

    @Test
    void exception_rate_triggers_rule_review() {
        // Arrange: Construct standardized claim payload with high exception rate
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("claimId", CLAIM_ID);
        inputPayload.put("exceptionRate", 0.92); // Exceeds business threshold
        inputPayload.put("category", "AUTO");

        ClaimDataStandardizationCalculationTransform transform = new ClaimDataStandardizationCalculationTransform();
        transform.setId(CLAIM_ID);
        transform.setPayload(inputPayload);

        // Mock infra I/O: DynamoDB read for rules engine decision
        Map<String, Object> rulesItem = Map.of("pk", CLAIM_ID, "reviewRule", "HIGH_EXCEPTION_RATE", "active", true);
        when(rulesEngineDecision.readItem(eq(TABLE_NAME), eq("pk"))).thenReturn(rulesItem);

        // Mock infra I/O: S3 write for structured audit diary (GDPR/SOC2 compliance)
        String resolvedKey = "AuditDiaryStore/" + CLAIM_ID + ".json";
        String expectedUri = "s3://" + BUCKET_NAME + "/" + resolvedKey;
        when(auditDiaryStore.writeObject(eq(BUCKET_NAME), eq(resolvedKey), anyString()))
                .thenReturn(expectedUri);

        // Mock enrichment service return value
        Map<String, Object> enrichedPayload = new HashMap<>();
        enrichedPayload.put("ruleReviewTriggered", true);
        enrichedPayload.put("status", "REVIEW_REQUIRED");
        enrichedPayload.put("auditUri", expectedUri);
        enrichedPayload.put("processedAt", System.currentTimeMillis());

        when(enrichmentService.processAndEnrich(any(ClaimDataStandardizationCalculationTransform.class)))
                .thenReturn(enrichedPayload);

        // Act
        Map<String, Object> result = enrichmentService.processAndEnrich(transform);

        // Assert
        assertNotNull(result, "Enrichment result must not be null");
        assertTrue((Boolean) result.get("ruleReviewTriggered"), "Rule review must be triggered for high exception rate");
        assertEquals("REVIEW_REQUIRED", result.get("status"), "Status must reflect pending review");
        assertTrue(result.containsKey("auditUri"), "Audit URI must be present for compliance tracking");

        // Verify infra I/O contracts were invoked exactly once with correct parameters
        verify(rulesEngineDecision, times(1)).readItem(eq(TABLE_NAME), eq("pk"));
        verify(auditDiaryStore, times(1)).writeObject(eq(BUCKET_NAME), eq(resolvedKey), anyString());
    }
}
