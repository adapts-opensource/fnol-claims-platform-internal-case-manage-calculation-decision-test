package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class InsuredEngagementOrchestrationDecisionTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT = "/api/insured-engagement/decision";

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void orchestration_decision_multiple_policy_match_resolve_task() {
        String payload = """
                {
                  "policy_number": "FL-DP3-2023-77221",
                  "risk_address": "456 Oak Ave, Tampa FL 33602",
                  "named_insured": "Jane Smith",
                  "date_of_loss": "2024-06-01",
                  "product_form": "DP3",
                  "cause_of_loss": "water",
                  "damage_severity": "low",
                  "occupancy_relationship": "tenant"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(ENDPOINT)
                .then()
                .statusCode(200)
                .body("duplicate_policy_flag", equalTo(true))
                .body("state", equalTo("Intake Review"))
                .body("tasks_generated", hasItem("Resolve Policy Match"))
                .body("claim_id", notNullValue())
                .body("acknowledgment_queued", equalTo(true));
    }
}
