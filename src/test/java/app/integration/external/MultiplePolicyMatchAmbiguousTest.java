package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimDataStandardizationEnrichmentValidationExternalTest {

    private static final String BASE_URL_ENV = "APP_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String ENDPOINT_PATH = "/api/claim-data-standardization-enrichment-validation";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv(BASE_URL_ENV) != null ? System.getenv(BASE_URL_ENV) : DEFAULT_BASE_URL;
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void fnol_multiple_policy_match_ambiguous() {
        String payload = """
            {
              "policy_number": "POL-87654321",
              "risk_address": "456 Oak Ave",
              "named_insured": "Jane Smith",
              "date_of_loss": "2024-06-01",
              "cause_of_loss": "Fire",
              "product_form": "DP3"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post(ENDPOINT_PATH)
            .then()
            .statusCode(201)
            .body("status", equalTo("Unmatched Policy"))
            .body("claim_id", notNullValue())
            .body("tasks", hasItem(hasEntry("type", "Resolve Policy Match")));
    }
}
