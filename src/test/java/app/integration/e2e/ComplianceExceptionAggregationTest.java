package app.integration.e2e;

import app.models.Claim;
import app.services.ClaimService;
import app.services.ComplianceAggregationService;
import app.services.DashboardService;
import app.services.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ComplianceExceptionAggregationTest {

    private ClaimService claimService;
    private ComplianceAggregationService complianceAggregationService;
    private DashboardService dashboardService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        // In an E2E environment, services are resolved from the application context.
        // For compilation, we assume generated stub implementations exist under src/main/java/app/.
        claimService = new ClaimService();
        complianceAggregationService = new ComplianceAggregationService();
        dashboardService = new DashboardService();
        taskService = new TaskService();
    }

    @Test
    void aggregate_coverage_review_exception_to_dashboard() {
        // Build inputs from test-case Inputs / Expected Results and the Constants JSON sidecar
        String policyNumber = "FL-2024-445566";
        String riskAddress = "789 Palm Ave, Tampa, FL";
        String causeOfLoss = "Water";
        String productForm = "HO3";
        String reporterType = "Named Insured";
        LocalDate policyCancellationDate = LocalDate.of(2024, 5, 14);
        LocalDate dateOfLoss = LocalDate.of(2024, 5, 15);

        Claim claimInput = new Claim();
        claimInput.setPolicyNumber(policyNumber);
        claimInput.setRiskAddress(riskAddress);
        claimInput.setCauseOfLoss(causeOfLoss);
        claimInput.setProductForm(productForm);
        claimInput.setReporterType(reporterType);
        claimInput.setPolicyCancellationDate(policyCancellationDate);
        claimInput.setDateOfLoss(dateOfLoss);

        // Step 1: Create claim and verify initial status
        Claim createdClaim = claimService.createClaim(claimInput);
        assertNotNull(createdClaim, "Claim must be persisted");
        assertEquals("Coverage Triage", createdClaim.getStatus(),
                "Claim status must transition to Coverage Triage upon coverage review rule trigger");

        // Step 2: Trigger compliance aggregation pipeline
        complianceAggregationService.process(createdClaim);

        // Step 3: Verify dashboard metric increment
        Map<String, Object> coverageMetrics = dashboardService.getAggregatedMetrics("Coverage Review Queue");
        assertNotNull(coverageMetrics, "Dashboard metrics must be available");
        assertTrue((long) coverageMetrics.getOrDefault("count", 0L) > 0L,
                "Coverage Review Queue metric must increment");

        // Step 4: Verify compliance exception aggregation contains the claim
        assertTrue(complianceAggregationService.existsInExceptionAggregation(policyNumber),
                "Claim must appear in compliance exception aggregation");

        // Step 5: Verify task generation
        List<String> generatedTasks = taskService.retrieveTasksForClaim(policyNumber);
        assertTrue(generatedTasks.stream().anyMatch(task -> "Review Coverage".equals(task)),
                "Task 'Review Coverage' must be generated");
    }
}
