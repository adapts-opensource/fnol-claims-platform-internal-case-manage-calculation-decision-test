package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimInitiationRoutingDecisionExternalTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_fnol_active_policy_match_and_claim_opening() {
        String payload = "{"
            + "\"policy_number\":\"POL-FL-987654\","
            + "\"risk_address\":\"123 Ocean Dr, Miami, FL 33139\","
            + "\"date_of_loss\":\"2024-05-15\","
            + "\"cause_of_loss\":\"wind\","
            + "\"reporter_name\":\"John Doe\","
            + "\"reporter_type\":\"insured\","
            + "\"product_form\":\"HO3\""
            + "}";

        Response response = given()
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/api/claim-initiation-routing/decision")
            .then()
            .extract().response();

        response.then()
            .statusCode(anyOf(is(200), is(201)))
            .body("claim_number", matchesPattern("CLM-.*-\\d{4}-\\d+"))
            .body("fnol_status", is("Claim Opened"))
            .body("triage_classification", notNullValue())
            .body("tasks", hasItems("Review FNOL", "Acknowledge Claim", "Assign Adjuster"))
            .body("acknowledgment_status", is("Queued"))
            .body("statutory_diaries", hasSize(greaterThanOrEqualTo(1)));
    }
}
