package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ClaimInitiationRoutingDecisionExternalTest {

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
    void submit_fnol_dol_outside_policy_period_coverage_review() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policy_number", "POL-FL-333444");
        payload.put("risk_address", "789 Gulf Blvd, Key West, FL 33040");
        payload.put("date_of_loss", "2023-01-01");
        payload.put("cause_of_loss", "hurricane");
        payload.put("reporter_name", "Bob Lee");
        payload.put("reporter_type", "insured");
        payload.put("product_form", "HO3");

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .post("/api/claim-initiation-routing/decision")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .body("status", equalTo("Coverage Triage"))
            .body("routing_decision", equalTo("Coverage Review"))
            .body("claim_number", notNullValue())
            .body("acknowledgment.sent", equalTo(true))
            .body("acknowledgment.type", equalTo("Coverage Reservation Notice"))
            .body("statutory_diaries", hasSize(greaterThan(0)))
            .body("statutory_diaries[*].due_date", notNullValue());
    }
}
