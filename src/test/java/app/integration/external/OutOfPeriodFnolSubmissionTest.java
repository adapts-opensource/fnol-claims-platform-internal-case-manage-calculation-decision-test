package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

public class ClaimDataStandardizationStateTransitionOrchestrationExternalTest {

    private static String baseUrl;

    @BeforeAll
    static void setup() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
    }

    @Test
    void orchestrate_out_of_policy_period_state_transition() {
        String payload = """
            {
              "payload": {
                "policy_number": "FL987654321",
                "risk_address": "456 Oak Ave Tampa FL 33602",
                "date_of_loss": "2023-01-10",
                "cause_of_loss": "water_damage",
                "product_form": "HO3",
                "reporter_type": "insured"
              }
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
            .post(baseUrl + "/api/claim-data-standardization/state-transition/orchestration")
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("claimStatus", equalTo("Coverage Triage"))
            .body("tasks", hasItem(hasKey("name")))
            .body("tasks[*].name", hasItem("Review Coverage"))
            .body("investigationStatus", equalTo("Open"))
            .body("coverageStatus", equalTo("Outside Policy Period"));
    }
}
