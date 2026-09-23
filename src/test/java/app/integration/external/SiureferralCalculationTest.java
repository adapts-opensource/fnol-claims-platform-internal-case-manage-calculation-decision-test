package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SubmitFnolSiuReferralCalculationTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";

    @Test
    void submit_fnol_siu_referral_calculation() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policy_number", "POL-SIU-2024-001");
        payload.put("date_of_loss", "2024-09-10");
        payload.put("product_form", "HO3");
        payload.put("cause_of_loss", "Fire");
        payload.put("reporter_type", "contractor");
        payload.put("late_reporting_days", 45);
        payload.put("prior_claims_count", 4);
        payload.put("estimated_damage", 50000);
        payload.put("channel", "api_intake");

        given()
                .baseUri(BASE_URL)
                .contentType("application/json")
                .body(payload)
        .when()
                .post("/api/fnol")
        .then()
                .statusCode(200)
                .body("state", equalTo("Claim Opened"))
                .body("policy_match_status", equalTo("Matched"))
                .body("initial_claim_type", hasItem("SIU referral candidate"))
                .body("tasks", hasItem(hasEntry("task_type", "SIU Referral Review")))
                .body("fraud_score", notNullValue())
                .body("fraud_score", greaterThanOrEqualTo(0));
    }
}
