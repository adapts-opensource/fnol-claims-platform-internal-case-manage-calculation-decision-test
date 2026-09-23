package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimDataStandardizationEnrichmentValidationExternalTest {

    private static final String FEATURE_SLUG = "claim-data-standardization/enrichment/validation";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void fnol_exact_policy_match_valid_dol() {
        String payload = """
            {
              "policy_number": "POL-12345678",
              "risk_address": "123 Main St",
              "named_insured": "John Doe",
              "date_of_loss": "2024-05-15",
              "cause_of_loss": "Wind",
              "product_form": "HO3"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/" + FEATURE_SLUG)
            .then()
            .statusCode(201)
            .body("claim_id", matchesPattern("CLM-FL01-2024-\\w+"))
            .body("status", is("Claim Opened"))
            .body("handling_path", is("Standard property claim"))
            .body("tasks", hasItem(hasEntry("type", "acknowledgment")));
    }
}
