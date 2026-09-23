package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FutureDateLossRejectedTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void reject_fnol_future_date_of_loss() {
        String payload = "{"
                + "\"policy_number\":\"POL-12345\","
                + "\"risk_address\":\"123 Main St\","
                + "\"named_insured_name\":\"John Doe\","
                + "\"date_of_loss\":\"2030-01-01\","
                + "\"cause_of_loss\":\"wind\","
                + "\"contact_method\":\"email\""
                + "}";

        given()
                .contentType(ContentType.JSON)
                .body(payload)
        .when()
                .post("/api/fnol/decision/validation")
        .then()
                .statusCode(422)
                .body("errors", hasItem(containsString("date_of_loss")))
                .body("errors", hasItem(containsString("cannot be in the future")));
    }
}
