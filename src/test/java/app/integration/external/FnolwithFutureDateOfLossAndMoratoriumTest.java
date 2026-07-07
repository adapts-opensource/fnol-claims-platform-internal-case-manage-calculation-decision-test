package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FNOLWithFutureDateOfLossAndMoratoriumTest {

    private static final String BASE_URL;

    @BeforeAll
    static void setUp() {
        BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    }

    @Test
    void submit_fnol_with_future_date_of_loss_during_moratorium() {
        String payload = """
                {
                  "policy_number": "FL987654321",
                  "risk_address": "456 Ocean Dr, Miami, FL 33139",
                  "named_insured": "Jane Smith",
                  "date_of_loss": "2025-12-01",
                  "cause_of_loss": "Hurricane",
                  "channel": "api_intake",
                  "moratorium_active": true
                }
                """;

        given()
                .baseUri(BASE_URL)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/fnol")
                .then()
                .statusCode(200)
                .body("fnol_state", equalTo("Unmatched FNOL"))
                .body("policy_match_status", equalTo("Matched but coverage_flags includes OutOfPeriod"))
                .body("coverage_flags", hasItem("OutOfPeriod"))
                .body("next_steps", equalTo("Intake Shell"))
                .body("tasks", hasItems("Review Coverage", "Diary Statutory Deadlines"))
                .body("error_message", containsString("moratorium active"));
    }
}
