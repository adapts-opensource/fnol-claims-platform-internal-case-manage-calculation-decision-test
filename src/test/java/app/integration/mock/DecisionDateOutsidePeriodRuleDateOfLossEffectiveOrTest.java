package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    private ClaimEnrichmentService claimEnrichmentService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        // Wire service with mocked external I/O to satisfy NFRs (security, compliance, observability)
        claimEnrichmentService = new ClaimEnrichmentService(auditDiaryStoreService, rulesEngineDecisionService, workflowTaskRouterService);

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate dateOfLoss = LocalDate.of(2022, 1, 15);
        LocalDate effectiveDate = LocalDate.of(2022, 3, 1);
        LocalDate expirationDate = LocalDate.of(2023, 3, 1);

        claimPayload = Map.of(
            "id", "claim-123",
            "date_of_loss", dateOfLoss.format(formatter),
            "policy_effective_date", effectiveDate.format(formatter),
            "policy_expiration_date", expirationDate.format(formatter),
            "status", "NEW",
            "routing_target", "PENDING"
        );
    }

    @Test
    void decision_date_outside_period_rule_date_of_loss_effective_or_expiration_cancellation_expected_outcome_capture_claim_route_to_coverage_review() {
        // Arrange: Simulate rule evaluation path where date_of_loss < effective_date
        // In production, ClaimEnrichmentService evaluates this internally.
        // We verify the external I/O contracts are triggered correctly per infra specs.

        // Act: Execute enrichment pipeline
        Map<String, Object> enrichedClaim = claimEnrichmentService.enrich(claimPayload);

        // Assert: Verify expected outcome per feature specification
        assertNotNull(enrichedClaim, "Enriched claim payload must not be null");
        assertEquals("CAPTURED", enrichedClaim.get("status"), "Claim status should be captured");
        assertEquals("COVERAGE_REVIEW", enrichedClaim.get("routing_target"), "Claim should route to coverage review");

        // Verify external I/O interactions (mocked) to satisfy infra contracts & NFRs
        verify(rulesEngineDecisionService).recordDecision(eq("claim-123"), any(Map.class));
        verify(workflowTaskRouterService).routeTask(eq("claim-123"), eq("COVERAGE_REVIEW"));
        verify(auditDiaryStoreService).storeAudit(eq("claim-123"), any(Map.class));
    }
}
