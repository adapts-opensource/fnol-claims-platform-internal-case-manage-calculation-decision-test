package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * External integration test for Claim Data Standardization: calculation: transformation.
 * Verifies input validation and rejection of malformed submissions.
 * 
 * NFR Alignment:
 * - Security: Validates input constraints to prevent injection/malformed payloads.
 * - Compliance: Ensures auditability of validation failures.
 * - Operability: Confirms clear error signaling for downstream consumers.
 */
public class ClaimDataStandardizationCalculationTransformationExternalTest {

    private static final String BASE_URL_ENV = "APP_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String API_ENDPOINT = "/api/claim-data-standardization/transformation";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv(BASE_URL_ENV);
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    @DisplayName("Reject submission with missing required fields")
    void reject_empty_required_fields() {
        // Inputs: raw_address=, date_of_loss_string=invalid, cause_description=, channel_id=portal, device_metadata={}
        // Expected: HTTP 422, validation errors for raw_address, date_of_loss_string, cause_description.
        
        String payload = "{"
            + "\"raw_address\": \"\","
            + "\"date_of_loss_string\": \"invalid\","
            + "\"cause_description\": \"\","
            + "\"channel_id\": \"portal\","
            + "\"device_metadata\": {}"
            + "}";

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post(API_ENDPOINT)
            .then()
            .statusCode(422)
            .body("validation_error_messages", hasItem(containsString("raw_address")))
            .body("validation_error_messages", hasItem(containsString("date_of_loss_string")))
            .body("validation_error_messages", hasItem(containsString("cause_description")))
            .body("normalized_output", nullValue());
    }
}
