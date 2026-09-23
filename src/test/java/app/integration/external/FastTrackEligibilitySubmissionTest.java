package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FastTrackEligibilitySubmissionTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_fast_track_eligible_fnol() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "POL-FL-2024-001");
        payload.put("risk_address", "123 Palm Ave");
        payload.put("date_of_loss", "2024-09-15");
        payload.put("cause_of_loss", "wind");
        payload.put("severity", "low");
        payload.put("has_attorney", false);
        payload.put("has_public_adjuster", false);
        payload.put("channel", "api");
        payload.put("tenant_code", "FL01");

        ValidatableResponse response = given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/claim-data-standardization/orchestration/decision")
                .then()
                .statusCode(200)
                .body("claim_number", matchesPattern("CLM-FL01-2024-\\d{4}"))
                .body("initial_claim_type", equalTo("fast_track_claim"))
                .body("fnol_state", equalTo("Claim Opened"))
                .body("generated_tasks", hasItems("Review FNOL", "Acknowledge Claim", "Diary Statutory Deadlines"));
    }
}
