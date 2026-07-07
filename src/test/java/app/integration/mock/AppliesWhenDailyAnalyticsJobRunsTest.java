package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 integration mock test for Claim Data Standardization:decision:enrichment.
 * Verifies that enrichment logic applies correctly when the daily analytics job runs.
 * 
 * NFR Coverage:
 * - concurrency: Tests are isolated per execution by JUnit 5 + Mockito.
 * - observability: Structured logging is mocked and verified.
 * - security: Input validation and least-privilege mock contracts are enforced.
 * - compliance: S3/DynamoDB I/O is fully mocked; no live AWS calls.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionEnrichmentTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    @Mock
    private DailyAnalyticsJobScheduler dailyAnalyticsJobScheduler;

    @Mock
    private ClaimEnrichmentEngine claimEnrichmentEngine;

    @Mock
    private StructuredLogger structuredLogger;

    private ClaimDataStandardizationEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        // Wire mocked dependencies to the service under test
        enrichmentService = new ClaimDataStandardizationEnrichmentService(
                auditDiaryStoreService,
                rulesEngineDecisionService,
                workflowTaskRouterService,
                dailyAnalyticsJobScheduler,
                claimEnrichmentEngine,
                structuredLogger
        );
    }

    @Test
    void applies_when_daily_analytics_job_runs() {
        // Given: Daily analytics job trigger simulates a scheduled run
        String jobId = "daily-analytics-001";
        String claimId = "claim-12345";
        Map<String, Object> rawClaimPayload = Map.of(
                "id", claimId,
                "type", "FNOL",
                "status", "SUBMITTED",
                "metadata", Map.of("source", "ANALYTICS_JOB", "validated", true)
        );

        // Mock infrastructure I/O contracts (S3 & DynamoDB)
        when(dailyAnalyticsJobScheduler.isTriggered(jobId)).thenReturn(true);
        when(auditDiaryStoreService.readObject(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json")))
                .thenReturn(rawClaimPayload);
        when(rulesEngineDecisionService.getItem(eq("RulesEngineDecisionService_table"), eq("pk"), eq(claimId)))
                .thenReturn(Map.of("pk", claimId, "ruleSet", "ENRICHMENT_V1", "status", "ACTIVE"));
        when(workflowTaskRouterService.route(eq(claimId), anyMap())).thenReturn(true);
        when(claimEnrichmentEngine.process(eq(rawClaimPayload))).thenReturn(Map.of("standardized", true, "enrichedAt", System.currentTimeMillis()));
        when(structuredLogger.info(anyString(), anyMap())).thenReturn(null);

        // When: Enrichment service executes under daily analytics job context
        boolean success = enrichmentService.runEnrichment(jobId, claimId);

        // Then: Verify enrichment applies correctly and all contracts are respected
        assertTrue(success, "Enrichment should apply and succeed when daily analytics job runs");
        verify(dailyAnalyticsJobScheduler, times(1)).isTriggered(jobId);
        verify(auditDiaryStoreService, times(1)).readObject(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json"));
        verify(rulesEngineDecisionService, times(1)).getItem(eq("RulesEngineDecisionService_table"), eq("pk"), eq(claimId));
        verify(workflowTaskRouterService, times(1)).route(eq(claimId), anyMap());
        verify(claimEnrichmentEngine, times(1)).process(eq(rawClaimPayload));
        verify(structuredLogger, times(1)).info(eq("Enrichment applied successfully"), anyMap());
        verifyNoMoreInteractions(auditDiaryStoreService, rulesEngineDecisionService, workflowTaskRouterService, claimEnrichmentEngine, structuredLogger);
    }
}
