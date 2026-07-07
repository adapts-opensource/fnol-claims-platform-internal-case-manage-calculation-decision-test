package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * External integration test for Multi-Channel FNOL Submission:state_transition:calculation.
 * Verifies duplicate detection logic when inputs match high-risk patterns.
 */
public class SubmitFnolDuplicateDetectionHighScoreTest {

    private static final String BASE_URL_ENV = "APP_BASE_URL";
    private static final String DEFAULT_URL = "http://localhost:8080";
    private static final String FEATURE_ROUTE = "/api/multi-channel-fnol-submission";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv(BASE_URL_ENV);
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : DEFAULT_URL;
    }

    @Test
    @DisplayName("Submit FNOL Duplicate Detection High Score")
    void submit_fnol_duplicate_detection_high_score() {
        String payload = "{"
                + "\"policy_number\":\"POL-EXIST-2024-005\","
                + "\"risk_address\":\"400 Palm Way, Naples, FL, 34102\","
                + "\"date_of_loss\":\"2024-03-12\","
                + "\"cause_of_loss\":\"Wind\","
                + "\"reporter\":\"Jane Doe\","
                + "\"damaged_area\":\"Roof\","
                + "\"channel\":\"api_intake\""
                + "}";

        given()
                .contentType("application/json")
                .body(payload)
        .when()
                .post(FEATURE_ROUTE)
        .then()
                .statusCode(200)
                .body("state", equalTo("Duplicate Review"))
                .body("duplicate_score", greaterThanOrEqualTo(0.85))
                .body("tasks.task_type", hasItem("Review Potential Duplicate Claim"))
                .body("matched_claim_ids", not(empty()))
                .body("duplicate_flag", equalTo(true));
    }
}
