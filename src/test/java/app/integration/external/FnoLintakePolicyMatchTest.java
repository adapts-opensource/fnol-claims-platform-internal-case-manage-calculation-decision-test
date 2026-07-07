package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FnoLIntakePolicyMatchTest {

    private static String baseUrl;

    @BeforeAll
    public static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    public void submit_fnol_intake_policy_match_and_triage() {
        String payload = """
            {
              "policy_number": "FL123456789",
              "risk_address": "123 Main St, Miami, FL 33101",
              "date_of_loss": "2024-05-15",
              "cause_of_loss": "wind",
              "product_form": "HO3",
              "reporter_type": "insured",
              "channel": "api_intake"
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/api/claim-data-standardization/decision/transformation")
            .then()
            .statusCode(201)
            .body("claim_id", matchesRegex("CLM-FL01-\\d{4}-\\d{4}"))
            .body("claim_status", equalTo("Claim Opened"))
            .body("initial_claim_type", equalTo("Standard property claim"))
            .body("tasks", hasItem(hasEntry("name", "Review FNOL")))
            .body("audit_log.tenant", notNullValue())
            .body("audit_log.timestamp", notNullValue());
    }
}
