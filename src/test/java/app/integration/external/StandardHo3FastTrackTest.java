package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimDataStandardizationDecisionValidationTest {
    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL") : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void validate_standard_ho3_fast_track_decision() {
        String payload = """
            {
              "policy_number": "POL-HO3-001",
              "risk_address": "123 Main St",
              "loss_date": "2024-05-20",
              "product_form": "HO3",
              "cause_of_loss": "Wind",
              "severity": "Low",
              "reporter_type": "Named Insured",
              "has_attorney": false,
              "has_public_adjuster": false,
              "has_aob": false
            }
            """;

        given()
                .contentType("application/json")
                .body(payload)
        .when()
                .post("/api/claim-data-standardization-decision-validation")
        .then()
                .statusCode(200)
                .body("claim_type", equalTo("Standard property claim"))
                .body("fast_track_eligible", equalTo(true))
                .body("claim_state", equalTo("Claim Opened"))
                .body("policy_match_status", equalTo("Matched"))
                .body("tasks_generated", hasItems("Review FNOL", "Acknowledge Claim", "Assign Adjuster"))
                .body("claim_number", matchesRegex("CLM-FL01-\\d{4}-\\d{6}"));
    }
}
