package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class CoverageUncertaintyTriageTest {
    private static String baseUrl;

    @BeforeAll
    static void setup() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_coverage_uncertainty_fnol() {
        String payload = """
            {
              "policy_number": "POL-EXP-2023-999",
              "risk_address": "456 Ocean Blvd",
              "date_of_loss": "2024-09-15",
              "cause_of_loss": "water",
              "policy_status": "expired",
              "policy_expiration_date": "2024-08-01",
              "channel": "portal",
              "tenant_code": "FL01"
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/api/claim-data-standardization/decision")
            .then()
            .statusCode(200)
            .body("claim_number", notNullValue())
            .body("initial_claim_type", equalTo("coverage_review_claim"))
            .body("fnol_state", equalTo("Coverage Triage"))
            .body("generated_tasks", hasItems("Review Coverage", "Diary Statutory Deadlines", "Request Missing Information"));
    }
}
