package app.integration.external;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * External integration tests for Claim Data Standardization:validation:decision.
 * Validates end-to-end flow against live test/staging endpoints.
 */
@Tag("external")
@Tag("claim-data-standardization")
@Tag("validation")
@Tag("decision")
@Tag("fnol")
class ClaimDataStandardizationValidationDecisionTest {

    private static String baseUrl;

    @BeforeAll
    static void setupBaseUri() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
    }

    @Test
    @DisplayName("Submit valid FNOL with active policy")
    void submit_valid_fnol_active_policy() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policy_number", "POL-12345");
        payload.put("risk_address", "123 Main St, Miami, FL 33101");
        payload.put("named_insured", "John Doe");
        payload.put("date_of_loss", "2024-05-15");
        payload.put("cause_of_loss", "wind");
        payload.put("product_form", "HO3");
        payload.put("channel", "insured_portal");

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/claim-data-standardization/validation/decision")
        .then()
            .statusCode(200)
            .body("claim_id", matchesPattern("CLM-FL01-2024-\\d{4}"))
            .body("claim_status", equalTo("Claim Opened"))
            .body("policy_match", equalTo("single"))
            .body("triage_path", equalTo("Standard property claim"))
            .body("task_created", equalTo("Review FNOL"));
    }
}
