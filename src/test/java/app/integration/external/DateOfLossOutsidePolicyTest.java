package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class InsuredEngagementTrackingValidationTest {
    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String FEATURE_SLUG = "insured-engagement-tracking-transformation-validation";
    private static final String ENDPOINT = "/api/" + FEATURE_SLUG;

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    @DisplayName("validate_date_of_loss_outside_policy_period")
    void validate_date_of_loss_outside_policy_period() {
        String requestBody = """
            {
              "policy_number": "POL-DP3-445566",
              "risk_address": "456 Oak Ave, Tampa FL 33602",
              "named_insured": "Jane Smith",
              "date_of_loss": "2023-12-31",
              "policy_effective_date": "2024-01-01",
              "policy_expiration_date": "2024-12-31",
              "cause_of_loss": "fire",
              "product_form": "DP3",
              "reporter_type": "agent"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .post(ENDPOINT)
            .then()
            .statusCode(200)
            .body("state", containsString("Coverage Triage"))
            .body("validation_flag", containsString("DateOfLossOutsidePolicyPeriod"))
            .body("tasks", hasItem(containsString("Review Coverage")))
            .body("claim_status", containsString("OPEN"))
            .log().ifValidationFails();
    }
}
