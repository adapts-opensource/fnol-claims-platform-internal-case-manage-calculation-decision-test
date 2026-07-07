package app.integration.e2e;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import app.models.ClaimDataStandardizationStateTransitionOrch;
import app.services.ClaimOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

public class ClaimDataStandardizationOrchestrationDecisionTest {

    private final ClaimOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    public ClaimDataStandardizationOrchestrationDecisionTest() {
        // E2E: Wire real application services directly without mocks
        this.orchestrationService = new ClaimOrchestrationService();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void activePolicyLowSeverityFastTrack() throws Exception {
        // Build inputs from test-case Inputs / Expected Results and Constants JSON sidecar
        String policyNumber = "POL-FL-1001";
        String riskAddress = "123 Main St";
        LocalDate dateOfLoss = LocalDate.of(2024, 5, 10);
        String causeOfLoss = "wind";
        int severityScore = 15;
        String reporterType = "insured";
        boolean attorneyFlag = false;
        boolean publicAdjusterFlag = false;
        boolean aobFlag = false;
        String productForm = "HO3";

        Map<String, Object> payload = objectMapper.createObjectNode()
                .put("policy_number", policyNumber)
                .put("risk_address", riskAddress)
                .put("date_of_loss", dateOfLoss.toString())
                .put("cause_of_loss", causeOfLoss)
                .put("severity_score", severityScore)
                .put("reporter_type", reporterType)
                .put("attorney_flag", attorneyFlag)
                .put("public_adjuster_flag", publicAdjusterFlag)
                .put("aob_flag", aobFlag)
                .put("product_form", productForm)
                .put("state", "Draft")
                .put("claim_type", "Pending")
                .put("tasks", List.of())
                .put("diaries", List.of())
                .put("claim_number", "")
                .toMap();

        ClaimDataStandardizationStateTransitionOrch inputEntity = new ClaimDataStandardizationStateTransitionOrch();
        inputEntity.setId(UUID.randomUUID().toString());
        inputEntity.setPayload(payload);

        // Execute orchestration decision against real services
        ClaimDataStandardizationStateTransitionOrch resultEntity = orchestrationService.processDecision(inputEntity);

        // Verify state transitions to Claim Opened
        assertEquals("Claim Opened", resultEntity.getPayload().get("state"));

        // Verify claim_type assigned as Fast-track claim
        assertEquals("Fast-track claim", resultEntity.getPayload().get("claim_type"));

        // Verify claim_number generated matching CLM-FL01-2024-XXXX format
        String claimNumber = (String) resultEntity.getPayload().get("claim_number");
        assertNotNull(claimNumber);
        assertTrue(claimNumber.matches("CLM-FL01-2024-\\d{4}"), "Claim number must match CLM-FL01-2024-XXXX format");

        // Verify standard tasks Review FNOL and Assign Adjuster created
        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) resultEntity.getPayload().get("tasks");
        assertTrue(tasks.contains("Review FNOL"), "Review FNOL task must be created");
        assertTrue(tasks.contains("Assign Adjuster"), "Assign Adjuster task must be created");

        // Verify statutory diary Claim acknowledgment due created
        @SuppressWarnings("unchecked")
        List<String> diaries = (List<String>) resultEntity.getPayload().get("diaries");
        assertTrue(diaries.contains("Claim acknowledgment due"), "Statutory diary Claim acknowledgment due must be created");

        // Verify no duplicate or coverage review tasks generated
        assertFalse(tasks.contains("Duplicate Check"), "Duplicate check task should not be generated");
        assertFalse(tasks.contains("Coverage Review"), "Coverage review task should not be generated");
    }
}
