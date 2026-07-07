package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class PolicyMatchDateValidationTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void validate_policy_match_and_date_success() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "policy_number": "HO3-2024-98765",
                          "risk_address": "123 Main St, Tallahassee FL 32301",
                          "date_of_loss": "2024-05-15",
                          "product_form": "HO3",
                          "named_insured": "John Doe"
                        }
                        """)
                .when()
                .post("/api/claim-data-standardization-decision-enrichment")
                .then()
                .statusCode(200)
                .body("policy_id", is("POL-FL-001"))
                .body("match_confidence_score", is(0.95))
                .body("date_validation_result", is("valid"))
                .body("triage_path", is("standard_property_claim"))
                .body("coverage_flags", hasItem("active"));
    }
}
