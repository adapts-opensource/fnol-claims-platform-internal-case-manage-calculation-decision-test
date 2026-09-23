package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimDataStandardizationValidationDecisionTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null && !System.getenv("APP_BASE_URL").isEmpty()
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";
    private static final String ENDPOINT_PATH = "/api/claim-data-standardization/validation/decision";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void validate_hw2_non_wind_coverage_review() {
        String payload = "{\"policy_number\":\"POL-HW2\",\"risk_address\":\"321 Beach Dr, Key West, FL 33040\",\"named_insured\":\"Charlie Davis\",\"date_of_loss\":\"2024-07-20\",\"cause_of_loss\":\"flood\",\"product_form\":\"HW2\",\"channel\":\"insured_portal\"}";

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post(ENDPOINT_PATH)
                .then()
                .statusCode(200)
                .body("claim_id", notNullValue())
                .body("claim_status", is("Coverage Triage"))
                .body("policy_match", is("single"))
                .body("triage_path", is("Coverage review claim"))
                .body("task_created", is("Review Coverage"));
    }
}
