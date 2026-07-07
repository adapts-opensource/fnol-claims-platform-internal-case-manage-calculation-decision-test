package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class EnrichDecisionHw2NonWindCauseValidationTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT = "/api/claim-data-standardization/enrichment/decision";

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void enrich_decision_hw2_non_wind_cause() {
        String payload = """
                {
                  "policy_number": "FL-HW2-2024-045",
                  "risk_address": "789 Beach Rd, Miami, FL",
                  "named_insured": "Bob Johnson",
                  "date_of_loss": "2024-07-20",
                  "product_form": "HW2",
                  "cause_of_loss": "Water",
                  "occupancy_type": "OwnerOccupied"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .post(ENDPOINT)
                .then()
                .statusCode(200)
                .body("policy_match_status", equalTo("Active"))
                .body("triage_path", equalTo("CoverageReviewClaim"))
                .body("product_validation_flag", equalTo("NonWindCauseInWindOnlyPolicy"))
                .body("task_queue_entries", hasItem("Review Coverage"))
                .body("task_queue_entries", hasItem("NonWind Cause Review"));
    }
}
