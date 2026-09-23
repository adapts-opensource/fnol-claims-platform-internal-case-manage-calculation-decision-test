package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimDataStandardizationDecisionTransformationExternalTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_fnol_intake_date_of_loss_outside_policy_period() {
        String payload = """
            {
              "policy_number": "FL987654321",
              "risk_address": "456 Oak Ave, Tampa, FL 33601",
              "date_of_loss": "2023-01-10",
              "policy_effective_date": "2023-06-01",
              "cause_of_loss": "fire",
              "product_form": "HO3",
              "reporter_type": "agent",
              "channel": "api_intake"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/claim-data-standardization/decision/transformation")
            .then()
            .statusCode(201)
            .body("claim_status", equalTo("Coverage Triage"))
            .body("coverage_review_required", is(true))
            .body("tasks", hasItem(containsString("Review Coverage")))
            .body("claim_number", nullValue())
            .body("audit_log", notNullValue())
            .body("audit_log", containsString("validation failure"))
            .body("audit_log", containsString("routing decision"));
    }
}
