package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DateValidationCoverageReviewTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void post_fnoL_standardization_date_outside_period() {
        String jsonPayload = "{"
            + "\"policy_number\":\"FL-2024-112233\","
            + "\"risk_address\":\"456 Ocean Dr Miami FL 33139\","
            + "\"date_of_loss\":\"2023-01-01\","
            + "\"cause_of_loss\":\"water_damage\","
            + "\"reporter_name\":\"Bob Jones\","
            + "\"channel_type\":\"internal_csr\""
            + "}";

        given()
            .contentType(ContentType.JSON)
            .body(jsonPayload)
            .when()
            .post("/api/fnol/standardization")
            .then()
            .statusCode(200)
            .body("state", equalTo("Coverage Triage"))
            .body("tasks", hasItem("Review Coverage"))
            .body("date_validation_status", equalTo("outside_policy_period"));
    }
}
