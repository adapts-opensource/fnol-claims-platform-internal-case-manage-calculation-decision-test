package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FnolEnrichmentUnmatchedTest {

    private static String baseUrl;

    @BeforeAll
    static void init() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
    }

    @Test
    void submit_fnol_unmatched_policy_enrichment() {
        String endpoint = "/api/multi-channel-fnol-submission/decision/enrichment";
        String payload = """
            {
              "policy_number": "POL-INVALID-999",
              "date_of_loss": "2024-05-10",
              "cause_of_loss": "fire",
              "product_form": "HO3",
              "reporter_type": "agent",
              "reporter_name": "Jane Agent",
              "risk_address": "456 Ocean Blvd",
              "severity": "medium",
              "tenant_code": "FL01",
              "channel": "agent_portal"
            }
            """;

        given()
            .baseUri(baseUrl)
            .contentType("application/json")
            .body(payload)
        .when()
            .post(endpoint)
        .then()
            .statusCode(200)
            .body("claim_number", notNullValue())
            .body("state", equalTo("Unmatched Policy"))
            .body("policy_match_status", equalTo("Unmatched"))
            .body("tasks", hasItem("Resolve Policy Match"))
            .body("acknowledgment_pending", equalTo(true));
    }
}
