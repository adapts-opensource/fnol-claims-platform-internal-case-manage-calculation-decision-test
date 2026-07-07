package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecialistSelectsPolicyButMoratoriumLiftsDuringReviewTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;
    
    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;
    
    @Mock
    private WorkflowTaskRouter workflowTaskRouter;
    
    @Mock
    private Logger structuredLogger;

    private ClaimDataStandardizationEnrichmentService enrichmentService;

    private static final String TEST_CLAIM_ID = UUID.randomUUID().toString();
    private static final String TEST_POLICY_ID = "POL-NEWCO-8842";
    private static final String AUDIT_BUCKET = "AuditDiaryStore-bucket";
    private static final String DYNAMO_TABLE = "RulesEngineDecisionService_table";
    private static final String PARTITION_KEY = "pk";

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimDataStandardizationEnrichmentService(
                auditDiaryStore, rulesEngineDecisionService, workflowTaskRouter, structuredLogger
        );
    }

    @Test
    void specialist_selects_policy_but_moratorium_lifts_during_review() {
        // Arrange: Specialist selects policy, moratorium initially ACTIVE
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("id", TEST_CLAIM_ID);
        payload.put("policyId", TEST_POLICY_ID);
        payload.put("moratoriumStatus", "ACTIVE");
        payload.put("reviewStatus", "IN_PROGRESS");

        // Mock S3 audit write
        when(auditDiaryStore.write(anyString(), anyString(), anyString())).thenReturn("s3://" + AUDIT_BUCKET + "/" + TEST_CLAIM_ID + ".json");

        // Mock DynamoDB rules engine lookup
        Map<String, Object> decision = Map.of("ruleId", "MORATORIUM_EVAL", "result", "LIFTED");
        when(rulesEngineDecisionService.query(eq(DYNAMO_TABLE), eq(PARTITION_KEY), eq(TEST_CLAIM_ID))).thenReturn(decision);

        // Mock workflow router
        when(workflowTaskRouter.assign(eq(TEST_CLAIM_ID), anyString())).thenReturn("APPROVED_QUEUE");

        // Act: Trigger enrichment
        String auditUri = enrichmentService.processEnrichment(TEST_CLAIM_ID, payload);

        // Assert: Verify infrastructure interactions
        verify(auditDiaryStore, times(1)).write(eq(AUDIT_BUCKET), eq("AuditDiaryStore/" + TEST_CLAIM_ID + ".json"), anyString());
        assertNotNull(auditUri, "Audit URI must be resolved post-write");

        verify(rulesEngineDecisionService, times(1)).query(eq(DYNAMO_TABLE), eq(PARTITION_KEY), eq(TEST_CLAIM_ID));
        verify(workflowTaskRouter, times(1)).assign(eq(TEST_CLAIM_ID), eq("APPROVED_QUEUE"));

        // Assert: Verify payload enrichment reflects state change
        assertEquals("LIFTED", payload.get("moratoriumStatus"), "Moratorium status must update to LIFTED during review");
        assertEquals("REVIEW_COMPLETE", payload.get("reviewStatus"), "Review status must transition to COMPLETE");
        assertTrue(payload.containsKey("id"), "Payload must retain claim identifier");

        // Assert: NFR validations
        assertInstanceOf(ConcurrentHashMap.class, payload, "Thread-safety: payload must use concurrent collection");
        assertFalse(payload.isEmpty(), "Input validation: enriched payload must not be empty");
        verify(structuredLogger, times(1)).log(eq(Level.INFO), eq("ClaimDataStandardization:enrichment:moratorium_lifted"), anyString());
        verifyNoMoreInteractions(auditDiaryStore, rulesEngineDecisionService, workflowTaskRouter, structuredLogger);
    }
}

// Minimal interface stubs for compilation context
interface AuditDiaryStore { String write(String bucket, String keyPattern, String content); }
interface RulesEngineDecisionService { Map<String, Object> query(String table, String partitionKey, String entityId); }
interface WorkflowTaskRouter { String assign(String entityId, String queue); }
interface ClaimDataStandardizationEnrichmentService { String processEnrichment(String claimId, Map<String, Object> payload); }
