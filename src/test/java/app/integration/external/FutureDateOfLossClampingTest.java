package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class FutureDateOfLossClampingTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";
    private static final String ENDPOINT = "/api/claim-data-standardization/transformation";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void clamp_future_date_to_current() {
        String expectedDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("raw_address", "456 Oak Ave, Tampa, FL");
        payload.put("date_of_loss_string", "2099-12-31");
        payload.put("cause_description", "Fire");
        payload.put("channel_id", "mobile");

        Map<String, String> deviceMetadata = new HashMap<>();
        deviceMetadata.put("os", "ios");
        payload.put("device_metadata", deviceMetadata);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post(ENDPOINT)
                .then()
                .statusCode(200)
                .body("standard_date_of_loss", equalTo(expectedDate))
                .body("warnings", hasItem("future_date_clamped"))
                .body("internal_cause_code", containsString("fire"));
    }
}
