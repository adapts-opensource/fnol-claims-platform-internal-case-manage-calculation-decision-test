package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ExpiredPolicyCoverageReviewTest {

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = System.getenv("APP_BASE_URL") != null 
                ? System.getenv("APP_BASE_URL") 
                : "http://localhost:8080";
    }

    @Test
    void validate_expired_policy_coverage_review_routing() {
        String payload = """
                {
                  "policy_number": "POL-FL-2024-002",
                  "risk_address": "456 Oak Ave",
                  "date_of_loss": "2024-06-20",
                  "cause_of_loss": "fire",
                  "product_form": "DP3",
                  "severity": "medium"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/claim-initiation-routing-decision-validation")
                .then()
                .statusCode(200)
                .body("claimStatus", equalTo("Coverage Triage"))
                .body("routingDecision", equalTo("Coverage review claim"))
                .body("tasks", hasItem("Review Coverage"))
                .body("acknowledgmentStatus", equalTo("pending"));
    }
}
