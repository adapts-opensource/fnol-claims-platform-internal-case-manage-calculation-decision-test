package app.integration.e2e;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import app.models.Claim;
import app.models.DashboardMetric;
import app.models.Task;
import app.services.ClaimService;
import app.services.DashboardAggregationService;
import app.services.TaskService;

public class FnolSubmissionDashboardAggregationTest {

    private ClaimService claimService;
    private DashboardAggregationService dashboardAggregationService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        // Wire real application services for E2E execution against local/test environment
        claimService = new ClaimService();
        dashboardAggregationService = new DashboardAggregationService();
        taskService = new TaskService();
    }

    @Test
    void aggregate_fnol_submission_to_dashboard_metrics() {
        // Build inputs from test-case Inputs / Expected Results and the Constants JSON sidecar
        String policyNumber = "FL-2024-889900";
        String riskAddress = "123 Main St, Miami, FL";
        String causeOfLoss = "Wind";
        String productForm = "HO3";
        String reporterType = "Named Insured";
        String dateOfLoss = "2024-05-15";
        String channel = "Insured Portal";

        Claim submission = new Claim();
        submission.setPolicyNumber(policyNumber);
        submission.setRiskAddress(riskAddress);
        submission.setCauseOfLoss(causeOfLoss);
        submission.setProductForm(productForm);
        submission.setReporterType(reporterType);
        submission.setDateOfLoss(dateOfLoss);
        submission.setChannel(channel);

        // Act: Submit FNOL intake to real application services
        Claim createdClaim = claimService.submitIntake(submission);

        // Assert: Claim record created with status Claim Opened
        assertEquals("Claim Opened", createdClaim.getStatus(), "Claim record should be created with status Claim Opened");

        // Assert: dashboard metric New Claims increments by 1 for date 2024-05-15
        DashboardMetric metric = dashboardAggregationService.getMetric("New Claims", dateOfLoss);
        assertNotNull(metric, "Dashboard metric New Claims should exist for date " + dateOfLoss);
        assertEquals(1, metric.getIncrement(), "Dashboard metric New Claims should increment by 1 for date " + dateOfLoss);

        // Assert: claim appears in New claims by day aggregation query
        boolean existsInQuery = dashboardAggregationService.queryNewClaimsByDay(dateOfLoss).stream()
                .anyMatch(c -> c.getPolicyNumber().equals(policyNumber));
        assertTrue(existsInQuery, "Claim should appear in New claims by day aggregation query");

        // Assert: acknowledgment task created
        Task task = taskService.getTaskByClaimId(createdClaim.getId());
        assertNotNull(task, "Acknowledgment task should be created");
        assertEquals("Acknowledgment", task.getType(), "Task type should be Acknowledgment");
    }
}
