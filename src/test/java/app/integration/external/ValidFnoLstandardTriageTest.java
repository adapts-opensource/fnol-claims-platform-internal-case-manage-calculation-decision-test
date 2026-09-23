package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class InsuredEngagementOrchestrationTest {

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void submit_valid_fnol_standard_triage_tasks() {
        String payload = """
            {
              "policy_number": "HO3-12345",
              "risk_address": "123 Main St",
              "date_of_loss": "2024-05-15",
              "cause_of_loss": "wind",
              "reporter_type": "insured",
              "channel": "api"
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/api/insured-engagement-orchestration")
            .then()
            .statusCode(201)
            .body("claim_id", notNullValue())
            .body("status", equalTo("Open"))
            .body("tasks", hasSize(greaterThanOrEqualTo(2)))
            .body("tasks[*].name", hasItems("Review FNOL", "Acknowledge Claim"))
            .body("diaries", hasSize(greaterThanOrEqualTo(1)))
            .body("diaries[*].name", hasItem("Claim acknowledgment due"));
    }
}
