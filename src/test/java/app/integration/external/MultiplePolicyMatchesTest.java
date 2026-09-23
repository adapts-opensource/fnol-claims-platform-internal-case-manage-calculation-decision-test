package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class MultiplePolicyMatchesTest {

    @BeforeAll
    static void setupBaseUri() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void handleMultiplePolicyMatches() {
        String payload = """
            {
              "policy_number": "POL-MULTI",
              "risk_address": "789 Pine Rd, Orlando, FL 32801",
              "named_insured": "Bob Johnson",
              "date_of_loss": "2024-08-10",
              "cause_of_loss": "water",
              "product_form": "DP3",
              "channel": "internal_csr"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/claim-data-standardization/validation/decision")
            .then()
            .statusCode(200)
            .body("claim_id", notNullValue())
            .body("claim_status", is("Unmatched Policy"))
            .body("policy_match", is("multiple"))
            .body("triage_path", is("Standard property claim"))
            .body("task_created", is("Resolve Policy Match"));
    }
}
