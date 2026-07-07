package app.integration.e2e;

import app.models.ClaimDataStandardizationDecisionValidation;
import app.services.ClaimValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Us01IntakeSpecialistSubmitsAndValidatesFnolDataE2eTest {

    private ClaimValidationService claimValidationService;

    @BeforeEach
    void setUp() {
        // Wire to real application services. Compile stubs are generated under src/main/java/app/ when no implementation package exists.
        claimValidationService = new ClaimValidationService();
    }

    @Test
    void us_01_intake_specialist_submits_and_validates_fnol_data() {
        // Build inputs from test-case Inputs / Expected Results and the Constants JSON sidecar
        String testCaseId = "us_01_intake_specialist_submits_and_validates_fnol_data";
        Map<String, Object> fnolPayload = Map.of(
            "policyholderId", "PH-123456789",
            "incidentDate", "2024-01-15T10:30:00Z",
            "claimType", "AUTO_COLLISION",
            "description", "Rear-end collision at intersection",
            "submissionChannel", "WEB_PORTAL",
            "agentId", "AG-98765"
        );

        ClaimDataStandardizationDecisionValidation inputEntity = new ClaimDataStandardizationDecisionValidation();
        inputEntity.setId(testCaseId);
        inputEntity.setPayload(fnolPayload);

        // Execute E2E flow against real services
        Instant start = Instant.now();
        ClaimDataStandardizationDecisionValidation result = claimValidationService.validateAndStandardize(inputEntity);
        Instant end = Instant.now();

        // Assert Expected Results
        assertNotNull(result, "System must return a validated claim data standardization decision");
        assertNotNull(result.getId(), "Result must contain a valid ID");
        assertTrue(result.getPayload().containsKey("policyMatchStatus"), "System must return policy match status");
        assertTrue(result.getPayload().containsKey("triagePath"), "System must return triage path");

        // Validate SLA: within 2 seconds
        Duration elapsed = Duration.between(start, end);
        assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) <= 0,
            "System must return policy match status and triage path within 2 seconds, but took " + elapsed);
    }
}
