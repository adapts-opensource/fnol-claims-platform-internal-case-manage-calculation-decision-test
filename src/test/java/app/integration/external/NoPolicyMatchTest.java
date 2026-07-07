package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class NoPolicyMatchExternalTest {

    private static String baseUrl;

    @BeforeAll
    static void setupBaseUrl() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void createUnmatchedFnolShell() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policy_number", "INVALID-000");
        payload.put("risk_address", "999 Unknown Blvd, Jacksonville, FL 32201");
        payload.put("named_insured", "Alice Brown");
        payload.put("date_of_loss", "2024-09-05");
        payload.put("cause_of_loss", "theft");
        payload.put("product_form", "HO3");
        payload.put("channel", "api_intake");

        given()
                .contentType("application/json")
                .body(payload)
        .when()
                .post("/api/claim-data-standardization/validation/decision")
        .then()
                .statusCode(200)
                .body("claim_id", notNullValue())
                .body("claim_status", is("Unmatched Policy"))
                .body("policy_match", is("none"))
                .body("triage_path", is("Standard property claim"))
                .body("task_created", is("Resolve Policy Match"));
    }
}
