package app.integration.e2e;

import app.models.FnolSubmissionRequest;
import app.models.FnolSubmissionResponse;
import app.services.FnolSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CatastropheTriageEnrichmentE2eTest {

    private FnolSubmissionService fnolSubmissionService;
    private Map<String, Object> constants;

    @BeforeEach
    void setUp() {
        // Initialize real application service (stubs generated under src/main/java/app/)
        fnolSubmissionService = new FnolSubmissionService();
        // Load constants from JSON sidecar fixtures
        constants = Map.of(
            "event_name", "Hurricane_Milton",
            "cause_of_loss", "hurricane",
            "date_of_loss", "2024-10-08",
            "product_form", "HO3",
            "severity_score", "high",
            "risk_address", "456 Beach Rd",
            "reporter_type", "insured",
            "loss_description", "Extensive wind and water damage"
        );
    }

    @Test
    void submit_fnol_catastrophe_triage_enrichment() {
        // Build input payload from test-case Inputs / Expected Results and Constants sidecar
        Map<String, Object> payload = Map.of(
            "event_name", constants.get("event_name"),
            "cause_of_loss", constants.get("cause_of_loss"),
            "date_of_loss", constants.get("date_of_loss"),
            "product_form", constants.get("product_form"),
            "severity_score", constants.get("severity_score"),
            "risk_address", constants.get("risk_address"),
            "reporter_type", constants.get("reporter_type"),
            "loss_description", constants.get("loss_description")
        );

        FnolSubmissionRequest request = new FnolSubmissionRequest();
        request.setPayload(payload);
        request.setTenantId("tenant_01");

        // Invoke real application service end-to-end (no mocks/stubs)
        FnolSubmissionResponse response = fnolSubmissionService.submit(request);

        // Assert Expected Results
        assertNotNull(response.getClaimNumber(), "claim_number generated with tenant-specific sequence");
        assertEquals("Catastrophe claim", response.getTriageClaimType(), "triage_claim_type=Catastrophe claim");
        assertEquals("Named storm", response.getTriageDimensions().getCatastropheStatus(), "triage_dimensions.catastrophe_status=Named storm");

        List<String> tasks = response.getTasks();
        assertTrue(tasks.contains("Catastrophe Assignment"), "tasks contains Catastrophe Assignment");
        assertTrue(tasks.contains("Diary Statutory Deadlines"), "tasks contains Diary Statutory Deadlines");

        List<String> diaryEntries = response.getDiary();
        boolean hasStatutoryDiary = diaryEntries.stream()
                .anyMatch(d -> d.contains("Claim acknowledgment due") && d.contains("statutory basis"));
        assertTrue(hasStatutoryDiary, "diary contains Claim acknowledgment due with statutory basis");
    }
}
