package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class InsuredEngagementStateTransitionExternalTest {

    @BeforeAll
    static void setup() {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_fnol_valid_policy_match_open_claim() {
        String payload = """
            {
              "policy_number": "POL-12345",
              "risk_address": "123 Main St",
              "date_of_loss": "2024-05-01",
              "cause_of_loss": "wind",
              "product_form": "HO3",
              "named_insured": "John Doe"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/fnol")
            .then()
            .statusCode(201)
            .body("state", equalTo("Claim Opened"))
            .body("claim_number", notNullValue())
            .body("policy_match", equalTo("success"))
            .body("policy_id", equalTo("POL-12345"))
            .body("initial_claim_type", equalTo("Standard property claim"))
            .body("tasks", hasItem("Assign Adjuster"));
    }
}
