package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DuplicateDetectionReviewTest {

    private static String baseUrl;

    @BeforeAll
    public static void setup() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    public void submit_fnol_duplicate_detection_review() {
        String payload = "{\"policy_number\":\"POL-EXISTING\",\"risk_address\":\"123 Main St\",\"date_of_loss\":\"2024-05-01\",\"cause_of_loss\":\"wind\",\"reporter_id\":\"REP-001\",\"damaged_area\":\"Roof\"}";

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/insured-engagement/state-transition")
            .then()
            .statusCode(201)
            .body("state", equalTo("Duplicate Review"))
            .body("similarity_score", equalTo(0.92))
            .body("tasks", hasItem("Review Potential Duplicate Claim"))
            .body("matched_claim_id", equalTo("CLM-98765"));
    }
}
