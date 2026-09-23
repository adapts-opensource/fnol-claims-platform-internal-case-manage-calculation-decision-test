package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DateLossValidationOutOfPeriodTest {
    private static final String ENDPOINT_PATH = "/api/claim-data-standardization/calculation/decision";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void standardize_date_loss_validation_out_of_period() {
        String payload = """
                {
                  "date_of_loss": "2023-12-01",
                  "policy_effective_date": "2024-01-01",
                  "policy_expiration_date": "2025-01-01",
                  "policy_id": "POL-FL-2024-001"
                }
                """;

        given()
                .contentType("application/json")
                .body(payload)
        .when()
                .post(ENDPOINT_PATH)
        .then()
                .statusCode(200)
                .body("DATE_VALIDATION_STATUS", equalTo("OUT_OF_PERIOD"))
                .body("routing_recommendation", equalTo("COVERAGE_REVIEW"))
                .body("coverage_review_flag", equalTo(true));
    }
}
