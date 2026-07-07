package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimInitiationRoutingDecisionCalculationTest {

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void route_dp3_water_coverage_review() {
        String payload = "{"
            + "\"policy_form\":\"DP3\","
            + "\"cause_of_loss\":\"water\","
            + "\"severity_estimate\":12000,"
            + "\"compliance_flags\":\"coverage_uncertainty\","
            + "\"channel\":\"agent_portal\","
            + "\"date_of_loss\":\"2024-09-10\","
            + "\"risk_address\":\"456 Oak Ave Tampa FL\""
            + "}";

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/claim-initiation-routing-decision-calculation")
            .then()
            .statusCode(200)
            .body("routing_path", equalTo("coverage_review_claim"))
            .body("sla_hours", equalTo(48))
            .body("tasks", hasItems("Review Coverage", "Request Missing Information"))
            .body("status", containsString("Triage -> Coverage Triage"))
            .body("handler_group", equalTo("coverage_specialist_queue"));
    }
}
