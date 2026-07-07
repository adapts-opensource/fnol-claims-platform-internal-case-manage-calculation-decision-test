package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

public class EnrichDecisionDoLOutsidePeriodTest {

    private static final String CLAIM_ENRICHMENT_DECISION_PATH = "/api/claim-data-standardization/enrichment/decision";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = baseUrl != null && !baseUrl.isEmpty() ? baseUrl : "http://localhost:8080";
    }

    @Test
    void enrich_decision_dol_outside_policy_period() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "FL-DP3-2023-088");
        payload.put("risk_address", "456 Ocean Dr, Miami, FL");
        payload.put("named_insured", "Jane Smith");
        payload.put("date_of_loss", "2024-06-15");
        payload.put("product_form", "DP3");
        payload.put("policy_expiration_date", "2024-04-01");
        payload.put("cause_of_loss", "Fire");
        payload.put("occupancy_type", "Rental");

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post(CLAIM_ENRICHMENT_DECISION_PATH)
        .then()
            .statusCode(200)
            .body("policy_match_status", equalTo("Coverage_Review"))
            .body("triage_path", equalTo("CoverageReviewClaim"))
            .body("flag", equalTo("DoL_Outside_Period"))
            .body("task_queue_entries", hasItems("Review Coverage", "Request Missing Information"));
    }
}
