package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class StandardAddressSubmissionTest {

    private static final String BASE_URL;

    static {
        String envUrl = System.getenv("APP_BASE_URL");
        BASE_URL = (envUrl != null && !envUrl.isBlank()) ? envUrl : "http://localhost:8080";
    }

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void normalize_standard_address_and_cause() {
        String payload = """
            {
              "raw_address": "123 Main St, Miami, FL",
              "date_of_loss_string": "2024-01-15",
              "cause_description": "Wind",
              "channel_id": "portal",
              "device_metadata": {"browser": "chrome"}
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/claim-data-standardization/transformation")
            .then()
            .statusCode(200)
            .body("normalized_address.street", containsString("ST"))
            .body("normalized_address.zip", notNullValue())
            .body("standard_date_of_loss", matchesRegex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"))
            .body("internal_cause_code", containsString("WIND"))
            .body("enriched_metadata.channel_id", equalTo("portal"))
            .body("enriched_metadata.timestamp", notNullValue());
    }
}
