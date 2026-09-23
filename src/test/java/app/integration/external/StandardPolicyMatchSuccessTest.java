package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimInitiationRoutingOrchestrationTransformationTests {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_fnol_standard_policy_match_success() {
        String payload = "{"
            + "\"policy_number\":\"POL-FL-2026-98765\","
            + "\"insured_name\":\"John Doe\","
            + "\"risk_address\":\"123 Palm Ave Miami FL 33101\","
            + "\"date_of_loss\":\"2026-05-15\","
            + "\"cause_of_loss\":\"Wind\","
            + "\"product_form\":\"HO3\","
            + "\"reporter_type\":\"Named Insured\""
            + "}";

        given()
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/api/claim-initiation-routing-orchestration-transformation")
        .then()
            .statusCode(201)
            .body("claim_number", matchesPattern("CLM-FL01-2026-\\d{8}"))
            .body("workflow_state", equalTo("Claim Opened"))
            .body("policy_match_score", greaterThanOrEqualTo(90))
            .body("triage_classification", equalTo("Standard property claim"))
            .body("tasks", hasItems("Review FNOL", "Acknowledge Claim", "Assign Adjuster"))
            .body("audit_event.tenant", notNullValue())
            .body("audit_event.actor", notNullValue())
            .body("audit_event.timestamp", notNullValue())
            .body("acknowledgment.queued", equalTo(true));
    }
}
