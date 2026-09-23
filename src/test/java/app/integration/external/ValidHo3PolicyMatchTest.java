package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class MultiChannelFnolSubmissionDecisionValidationExternalTest {

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void submit_fnol_valid_ho3_match() {
        String payload = """
                {
                  "policy_number": "POL-12345",
                  "risk_address": "123 Main St",
                  "named_insured_name": "John Doe",
                  "date_of_loss": "2024-05-15",
                  "cause_of_loss": "wind",
                  "contact_method": "email"
                }
                """;

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/multi-channel-fnol-submission-decision-validation")
                .then()
                .statusCode(200)
                .body("matched_policy_id", notNullValue())
                .body("routing_code", equalTo("standard_property_claim"))
                .body("acknowledgment_id", notNullValue())
                .body("claim_status", equalTo("Submitted"));
    }
}
