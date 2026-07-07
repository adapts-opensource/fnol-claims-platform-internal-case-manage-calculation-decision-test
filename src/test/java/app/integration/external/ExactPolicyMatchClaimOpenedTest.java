package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SubmitFnolExactPolicyMatchClaimOpenedTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_fnol_exact_policy_match_claim_opened() {
        String payload = """
            {
              "policy_number": "POL-HO3-2024-001",
              "risk_address": "100 Ocean Dr, Miami, FL, 33139",
              "named_insured": "John Smith",
              "date_of_loss": "2024-05-15",
              "product_form": "HO3",
              "cause_of_loss": "Wind",
              "channel": "insured_portal"
            }
            """;

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/multi-channel-fnol-submission")
        .then()
            .statusCode(201)
            .body("state", equalTo("Claim Opened"))
            .body("claim_number", matchesPattern("CLM-FL01-2024-.*"))
            .body("match_confidence_score", greaterThanOrEqualTo(0.95))
            .body("policy_status", equalTo("Active"))
            .body("policy_match_status", equalTo("ExactMatch"));
    }
}
