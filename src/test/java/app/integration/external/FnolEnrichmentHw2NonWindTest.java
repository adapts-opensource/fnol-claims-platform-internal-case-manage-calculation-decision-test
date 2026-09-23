package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FnolEnrichmentHW2NonWindTest {

    private static final String BASE_URL;

    @BeforeAll
    public static void setup() {
        BASE_URL = System.getenv("APP_BASE_URL") != null
                ? System.getenv("APP_BASE_URL")
                : "http://localhost:8080";
    }

    @Test
    public void submit_fnol_hw2_non_wind_coverage_review() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "POL-HW2-2024-001");
        payload.put("date_of_loss", "2024-05-10");
        payload.put("cause_of_loss", "water_damage");
        payload.put("product_form", "HW2");
        payload.put("reporter_type", "insured");
        payload.put("risk_address", "789 Gulf Stream Way");
        payload.put("severity", "high");
        payload.put("tenant_code", "FL01");
        payload.put("channel", "insured_portal");

        given()
            .baseUri(BASE_URL)
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/multi-channel-fnol-submission")
        .then()
            .statusCode(200)
            .body("claim_number", notNullValue())
            .body("claim_type", equalTo("Coverage review claim"))
            .body("product_validation.flag", equalTo("Non-wind peril on wind-only form"))
            .body("tasks", hasItem("Review Coverage"))
            .body("state", equalTo("Coverage Triage"));
    }
}
