package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FastTrackStandardClaimTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    // Endpoint derived from feature slug: Multi-Channel FNOL Submission:orchestration:decision
    private static final String ENDPOINT = "/api/fnol-orchestration-decision";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void submit_fnol_api_fast_track_triage() {
        String payload = """
                {
                  "channel": "API",
                  "policy_number": "FL-HO3-998877",
                  "date_of_loss": "2024-06-15",
                  "cause_of_loss": "wind",
                  "product": "HO3",
                  "severity": "low",
                  "insured_name": "John Doe",
                  "risk_address": "123 Main St Miami FL 33101",
                  "attorney_flag": false,
                  "public_adjuster_flag": false,
                  "catastrophe_code": null
                }
                """;

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post(ENDPOINT)
                .then()
                .statusCode(201)
                .body("claim_number", startsWith("CLM-FL01-"))
                .body("state", equalTo("Claim Opened"))
                .body("claim_type", equalTo("Fast-track claim"))
                .body("tasks", hasItems("Review FNOL", "Acknowledge Claim"))
                .body("diaries", hasItems("Claim acknowledgment due", "Investigation start due"));
    }
}
