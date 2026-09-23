package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class MultiChannelFnolSubmissionStateTransitionCalculationExternalTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL") : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    @DisplayName("submit_fnol_date_of_loss_outside_period_coverage_triage")
    void submitFnolDateOfLossOutsidePeriodCoverageTriage() {
        String payload = "{"
                + "\"policy_number\":\"POL-EXP-2024-001\","
                + "\"risk_address\":\"300 Lake Ave, Orlando, FL, 32801\","
                + "\"date_of_loss\":\"2025-01-20\","
                + "\"product_form\":\"HO3\","
                + "\"cause_of_loss\":\"Fire\","
                + "\"channel\":\"internal_csr\""
                + "}";

        given()
                .contentType("application/json")
                .body(payload)
                .post("/api/multi-channel-fnol-submission")
                .then()
                .statusCode(200)
                .body("state", equalTo("Coverage Triage"))
                .body("coverage_uncertainty", equalTo(true))
                .body("policy_match_status", equalTo("Matched"))
                .body("policy_expiration_date", lessThan("2025-01-20"))
                .body("tasks", hasItem(hasEntry("task_type", "Review Coverage")));
    }
}
