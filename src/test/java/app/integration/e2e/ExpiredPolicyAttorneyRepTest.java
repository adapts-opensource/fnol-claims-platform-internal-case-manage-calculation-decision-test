package app.integration.e2e;

import app.models.ClaimSubmissionRequest;
import app.models.ClaimSubmissionResponse;
import app.services.ClaimOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E Test for Claim Data Standardization: Orchestration Decision.
 * Verifies handling of Expired Policy with Attorney Representation and High Severity.
 */
@SpringBootTest
public class ClaimDataStandardizationOrchestrationDecisionE2eTest {

    @Autowired
    private ClaimOrchestrationService claimOrchestrationService;

    @Test
    public void expired_policy_attorney_represented_coverage_review() {
        // Arrange: Build inputs from test-case Inputs
        ClaimSubmissionRequest request = new ClaimSubmissionRequest();
        request.setPolicyNumber("POL-FL-2002");
        request.setRiskAddress("456 Oak Ave");
        request.setDateOfLoss("2024-06-15");
        request.setCauseOfLoss("water");
        request.setSeverityScore(85);
        request.setReporterType("attorney");
        request.setAttorneyFlag(true);
        request.setPublicAdjusterFlag(false);
        request.setAobFlag(false);
        request.setPriorClaimPolicy("POL-FL-2002");
        request.setPriorClaimDate("2024-06-10");
        request.setProductForm("HO3");

        // Act: Invoke real service
        ClaimSubmissionResponse response = claimOrchestrationService.submitClaim(request);

        // Assert: Verify Expected Results
        // 1. State transitions to Coverage Triage
        assertEquals("Coverage Triage", response.getState(), 
            "State should transition to Coverage Triage for high severity/attorney case");

        // 2. Claim type assigned as Represented claim
        assertEquals("Represented", response.getClaimType(), 
            "Claim type should be assigned as Represented due to attorney_flag=true");

        // 3. Claim number generated matching CLM-FL01-2024-XXXX format
        String claimNumber = response.getClaimNumber();
        assertTrue(Pattern.matches("CLM-FL01-2024-\\d{4}", claimNumber), 
            "Claim number format should match CLM-FL01-2024-XXXX");

        // 4. Tasks created include Attorney Representation Review and Review Coverage
        List<String> tasks = response.getTasks();
        assertNotNull(tasks, "Tasks list should not be null");
        assertTrue(tasks.contains("Attorney Representation Review"), 
            "Task 'Attorney Representation Review' should be created");
        assertTrue(tasks.contains("Review Coverage"), 
            "Task 'Review Coverage' should be created for coverage review classification");

        // 5. Conditional SIU Referral Review task generated due to fraud indicators
        // (High severity + Attorney + Prior Claim overlap can trigger fraud indicators)
        assertTrue(tasks.contains("SIU Referral Review"), 
            "Task 'SIU Referral Review' should be generated due to fraud indicators");

        // 6. Duplicate review task created due to overlapping policy/risk/date
        assertTrue(tasks.contains("Duplicate Review"), 
            "Task 'Duplicate Review' should be created due to overlapping policy/prior_claim_policy");

        // 7. Statutory diary Representation document due created
        assertTrue(tasks.contains("Statutory Diary Representation"), 
            "Statutory Diary Representation document due should be created");
    }
}
