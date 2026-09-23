package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimDataStandardizationTransformationExternalTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void fallback_to_unknown_cause_code() {
        String payload = """
            {
              "raw_address": "789 Pine Rd, Orlando, FL",
              "date_of_loss_string": "2024-06-01",
              "cause_description": "Weird damage",
              "channel_id": "api",
              "device_metadata": {"version": "1.0"}
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/claim-data-standardization/transformation")
            .then()
            .statusCode(200)
            .body("internal_cause_code", equalTo("Unknown"))
            .body("review_required", is(true))
            .body("warnings", hasItem("unmapped_cause_warning"));
    }
}
