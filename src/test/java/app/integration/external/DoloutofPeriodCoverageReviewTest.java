package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DolOutOfPeriodCoverageReviewTest {

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void submit_fnol_dol_out_of_period_coverage_review() {
        String payload = """
                {
                  "policy_number": "POL-EXPIRED",
                  "risk_address": "456 Expired St",
                  "date_of_loss": "2024-05-01",
                  "cause_of_loss": "water",
                  "named_insured": "Bob Jones"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/fnol")
                .then()
                .statusCode(201)
                .body("state", equalTo("Coverage Triage"))
                .body("dol_validation_status", equalTo("out-of-period"))
                .body("initial_claim_type", equalTo("Coverage review claim"))
                .body("tasks", hasItem("Review Coverage"));
    }
}
