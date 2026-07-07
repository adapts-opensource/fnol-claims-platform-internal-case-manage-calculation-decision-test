package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimInitiationRoutingDecisionValidationExternalTest {
    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void validate_valid_policy_match_fast_track_routing() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "policy_number": "POL-FL-2024-001",
                  "risk_address": "123 Main St",
                  "date_of_loss": "2024-05-15",
                  "cause_of_loss": "wind",
                  "product_form": "HO3",
                  "severity": "low"
                }
                """)
        .when()
            .post("/api/claim-initiation-routing-decision-validation")
        .then()
            .statusCode(200)
            .body("claimStatus", equalTo("Claim Opened"))
            .body("routingDecision", equalTo("Fast-track claim"))
            .body("claimNumber", matchesRegex("CLM-FL01-\\d{4}-\\d{5}"))
            .body("task", equalTo("Review FNOL created"));
    }
}
