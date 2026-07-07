package app.integration.e2e;

import app.models.ClaimDataStandardizationDecisionValidation;
import app.services.ClaimSubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class ClaimDataStandardizationDecisionValidationE2E {

    private ClaimSubmissionService claimSubmissionService;

    @BeforeEach
    void setUp() {
        // Wire real services as per E2E guidelines; stubs compile under src/main/java/app/
        claimSubmissionService = new ClaimSubmissionService();
    }

    @Test
    void us_02_policyholder_submits_fnol_via_mobile_app() {
        // Constants sidecar fixtures for E2E input construction
        String expectedEndpoint = "/api/v1/fnol/submit";
        int expectedStatusCode = 202;
        Duration maxWait = Duration.ofMinutes(5);

        // Build input from test-case Inputs / Expected Results and Constants JSON sidecar
        Map<String, Object> payload = Map.of(
            "claim_type", "PROPERTY_DAMAGE",
            "incident_date", Instant.now().toString(),
            "description", "Policyholder experienced property damage and opened mobile app"
        );

        ClaimDataStandardizationDecisionValidation input = new ClaimDataStandardizationDecisionValidation(
            "fnol-" + Instant.now().toEpochMilli(),
            payload
        );

        Instant startTime = Instant.now();
        String acknowledgmentId = claimSubmissionService.submitFnol(input, expectedEndpoint);

        Assertions.assertEquals(expectedStatusCode, 202, "FNOL submission should return accepted status");
        Assertions.assertNotNull(acknowledgmentId, "Acknowledgment ID must be returned upon submission");

        // Expected Results: Acknowledgment delivered within 5 minutes
        boolean acknowledged = claimSubmissionService.waitForAcknowledgment(acknowledgmentId, maxWait, startTime);
        Assertions.assertTrue(acknowledged, "Acknowledgment must be delivered within 5 minutes");
    }
}
