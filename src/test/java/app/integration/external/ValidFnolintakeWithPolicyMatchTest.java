package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SubmitValidFnolWithActivePolicyMatchTest {

    private static String baseUrl;

    @BeforeAll
    public static void setUpBaseUri() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    public void submit_valid_fnol_with_active_policy_match() {
        String payload = """
            {
                "policy_number": "FL123456789",
                "risk_address": "123 Main St, Miami, FL 33101",
                "named_insured": "John Doe",
                "date_of_loss": "2024-05-15",
                "cause_of_loss": "Wind",
                "channel": "insured_portal"
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/api/insured-engagement/fnol")
        .then()
            .statusCode(200)
            .body("claim_number", matchesPattern("CLM-FL01-2024-\\d+"))
            .body("policy_match_status", is("Matched"))
            .body("fnol_state", is("Claim Opened"))
            .body("tasks", hasItem("Assign Adjuster"))
            .body("tasks", hasItem("Acknowledge Claim"));
    }
}
