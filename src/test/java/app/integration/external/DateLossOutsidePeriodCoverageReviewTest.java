package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import java.util.Map;

public class OrchestrationDecisionDateLossOutsidePolicyPeriodCoverageReviewTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT = "/api/orchestration-decision";

    private static final Map<String, Object> PAYLOAD = Map.of(
        "policy_number", "FL-HW2-2024-55443",
        "risk_address", "789 Pine Rd, Orlando FL 32801",
        "named_insured", "Bob Johnson",
        "date_of_loss", "2024-08-20",
        "product_form", "HW2",
        "cause_of_loss", "fire",
        "damage_severity", "high",
        "policy_expiration_date", "2024-07-31"
    );

    @Test
    void orchestrationDecisionDateLossOutsidePolicyPeriodCoverageReview() {
        given()
            .baseUri(BASE_URL)
            .contentType("application/json")
            .body(PAYLOAD)
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(200)
            .body("coverage_status", equalTo("Outside_Period"))
            .body("state", equalTo("Coverage Triage"))
            .body("tasks_generated", hasItem("Review Coverage"))
            .body("tasks_generated", hasItem("Diary Statutory Deadlines"))
            .body("claim_opened", equalTo(true))
            .body("coverage_review_flag", equalTo(true))
            .body("acknowledgment_sent", equalTo(true));
    }
}
