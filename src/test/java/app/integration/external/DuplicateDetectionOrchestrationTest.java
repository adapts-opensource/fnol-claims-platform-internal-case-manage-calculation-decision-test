package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DuplicateDetectionOrchestrationTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String API_ENDPOINT = "/api/claim-data-standardization";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void post_fnoL_standardization_duplicate_detected() {
        String payload = """
            {
              "policy_number": "FL-2024-445566",
              "risk_address": "789 Bay St Miami FL 33140",
              "date_of_loss": "2024-06-01",
              "cause_of_loss": "hurricane",
              "reporter_name": "Alice Brown",
              "channel_type": "api_intake"
            }
            """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_ENDPOINT)
                .then()
                .statusCode(200)
                .body("tasks", hasItem("Review Potential Duplicate Claim"))
                .body("duplicate_detection_status", equalTo("likely_match"));
    }
}
