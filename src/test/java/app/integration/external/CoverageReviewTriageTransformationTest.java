package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class CoverageReviewTriageTransformationTest {

    private static final String TRANSFORMATION_ENDPOINT = "/api/claim-initiation-routing/transformation";
    private static String baseUrl;

    @BeforeAll
    static void setup() {
        baseUrl = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    }

    @Test
    void transform_fnol_coverage_review_hw2_nonwind() {
        String payload = """
            {
              "tenant_code": "FL01",
              "policy_number": "HW2-123456",
              "date_of_loss": "2024-08-15",
              "cause_of_loss": "water",
              "product_form": "HW2",
              "severity": "medium",
              "channel": "agent"
            }
            """;

        given()
                .baseUri(baseUrl)
                .contentType("application/json")
                .body(payload)
                .post(TRANSFORMATION_ENDPOINT)
                .then()
                .statusCode(201)
                .body("claim_number", notNullValue())
                .body("initial_claim_type", equalTo("Coverage review claim"))
                .body("state", equalTo("Coverage Triage"))
                .body("tasks", hasItem(hasEntry("name", "Review Coverage")))
                .body("routing_path", equalTo("coverage specialist queue"));
    }
}
