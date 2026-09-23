package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class OrchestrationDecisionPolicyMatchStandardTriageTest {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : DEFAULT_BASE_URL;
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void orchestration_decision_policy_match_standard_triage() {
        String payload = """
            {
              "policy_number": "FL-HO3-2024-88991",
              "risk_address": "123 Main St, Miami FL 33101",
              "named_insured": "John Doe",
              "date_of_loss": "2024-05-15",
              "product_form": "HO3",
              "cause_of_loss": "wind",
              "damage_severity": "moderate"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/orchestration/decision")
        .then()
            .statusCode(200)
            .body("claim_id", matchesPattern("CLM-FL01-2024-\\d{5}"))
            .body("state", equalTo("Claim Opened"))
            .body("triage_path", equalTo("standard_review"))
            .body("acknowledgment_sent", is(true))
            .body("tasks_generated", hasItems("Acknowledge Claim", "Assign Adjuster"));
    }
}
