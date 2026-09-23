package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Tag("external")
@Tag("claim-data-standardization")
public class PolicyMatchExactByNumberTest {
    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
    }

    @Test
    void standardize_policy_match_exact_by_number() {
        // Arrange: Build request payload with exact policy match inputs
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("policy_number", "POL-FL-2024-001");
        payload.put("risk_address", "100 Ocean Dr Miami FL 33101");
        payload.put("named_insured", "Jane Smith");
        payload.put("date_of_loss", "2024-06-15");
        payload.put("product_form", "HO3");

        // Act & Assert: Call live HTTP endpoint and verify response
        given()
            .baseUri(baseUrl)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/api/claim-data-standardization")
        .then()
            .statusCode(200)
            .body("POLICY_MATCH_STATUS", equalTo("Exact"))
            .body("policy_id", equalTo("POL-FL-2024-001"))
            .body("match_score", greaterThanOrEqualTo(0.85));
    }
}
