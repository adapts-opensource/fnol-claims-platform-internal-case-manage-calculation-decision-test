package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class MultiplePolicyMatchRoutingTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT = "/api/fnol";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void submit_fnol_multiple_policy_match_resolution_task() {
        String payload = """
            {
              "policy_number": "POL-FL-111222",
              "risk_address": "456 Palm Ave, Tampa, FL 33602",
              "date_of_loss": "2024-06-10",
              "cause_of_loss": "water",
              "reporter_name": "Jane Smith",
              "reporter_type": "agent",
              "product_form": "HO3"
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("status", equalTo("Intake Review"))
            .body("claim_number", nullValue())
            .body("acknowledgment_sent", equalTo(false))
            .body("lifecycle_states", not(containsString("Claim Opened")))
            .body("tasks", hasItem(allOf(
                hasEntry("name", "Resolve Policy Match"),
                hasEntry("assigned_to", "Claims Intake")
            )));
    }
}
