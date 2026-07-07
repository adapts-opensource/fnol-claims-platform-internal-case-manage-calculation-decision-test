package app.integration.e2e;

import app.models.Claim;
import app.models.TriageResult;
import app.models.Task;
import app.services.ClaimTransformationService;
import app.services.TriageEngine;
import app.services.TaskOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MultiChannelIntakeNormalizationTest {

    private ClaimTransformationService transformationService;
    private TriageEngine triageEngine;
    private TaskOrchestrator taskOrchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Wire real services pointing to local test stack (APP_BASE_URL)
        // Compile stubs under src/main/java/app/ are generated when no implementation package exists
        transformationService = new ClaimTransformationService();
        triageEngine = new TriageEngine();
        taskOrchestrator = new TaskOrchestrator();
    }

    @Test
    void transform_api_intake_to_unified_claim_model() throws Exception {
        // Build inputs from test-case Inputs and the Constants JSON sidecar
        String constantsJson = """
            {
              "channel": "API Intake",
              "policy_number": "FL-2024-112233",
              "risk_address": "456 Ocean Dr, Key West, FL",
              "cause_of_loss": "Fire",
              "product_form": "DP3",
              "reporter_type": "Agent",
              "damage_areas": ["Roof", "Interior"],
              "intake_timestamp": "2024-05-20T14:30:00Z"
            }
            """;

        Map<String, Object> inputs = objectMapper.readValue(constantsJson, Map.class);

        // Invoke real transformation service end-to-end
        Claim unifiedClaim = transformationService.transformToIntegratedModel(inputs);

        // Expected: Claim created, data normalized to unified model including address standardization and date parsing
        assertNotNull(unifiedClaim.getClaimId(), "Claim created");
        assertEquals("456 OCEAN DR, KEY WEST, FL 33040", unifiedClaim.getRiskAddress(), "Address standardized");
        assertNotNull(unifiedClaim.getParsedIntakeDate(), "Date parsed");
        assertTrue(unifiedClaim.isPolicyMatched(), "Policy match succeeds");

        // Expected: triage assigns Standard property claim
        TriageResult triage = triageEngine.assignTriage(unifiedClaim);
        assertEquals(TriageResult.Category.STANDARD, triage.getCategory(), "Triage assigns Standard property claim");

        // Expected: task Review FNOL generated, task Assign Adjuster generated
        List<Task> tasks = taskOrchestrator.generateTasks(unifiedClaim, triage);
        assertEquals(2, tasks.size(), "Correct number of tasks generated");
        assertTrue(tasks.stream().anyMatch(t -> t.getType().equals("Review FNOL")), "Task Review FNOL generated");
        assertTrue(tasks.stream().anyMatch(t -> t.getType().equals("Assign Adjuster")), "Task Assign Adjuster generated");
    }
}
