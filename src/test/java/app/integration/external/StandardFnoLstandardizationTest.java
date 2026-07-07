package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class StandardFnoLStandardizationTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT_PATH = "/api/fnol-standardization";

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void post_fnoL_standardization_success() {
        String payload = """
            {
              "policy_number": "FL-2024-889900",
              "risk_address": "123 Palm Ave Miami FL 33101",
              "date_of_loss": "2024-05-15",
              "cause_of_loss": "wind",
              "reporter_name": "John Doe",
              "channel_type": "insured_portal"
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
            .when()
            .post(ENDPOINT_PATH)
            .then()
            .statusCode(200)
            .body("claim_id", notNullValue())
            .body("claim_number", matchesRegex("CLM-FL01-\\d{4}-\\d{8}"))
            .body("state", equalTo("Claim Opened"))
            .body("tasks", hasItems("Review FNOL", "Acknowledge Claim"));
    }
}
