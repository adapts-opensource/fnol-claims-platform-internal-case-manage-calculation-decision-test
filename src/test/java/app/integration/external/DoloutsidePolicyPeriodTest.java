package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DolOutsidePolicyPeriodTest {

    private static final String baseUrl = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String endpoint = "/api/claim-data-standardization";

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = baseUrl;
    }

    @Test
    public void validate_dol_outside_policy_period() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "POL-67890");
        payload.put("risk_address", "456 Oak Ave, Tampa, FL 33601");
        payload.put("named_insured", "Jane Smith");
        payload.put("date_of_loss", "2023-12-01");
        payload.put("cause_of_loss", "fire");
        payload.put("product_form", "HO3");
        payload.put("channel", "agent_assisted");

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post(endpoint)
        .then()
            .statusCode(200)
            .body("claim_id", notNullValue())
            .body("claim_status", equalTo("Coverage Triage"))
            .body("policy_match", equalTo("single"))
            .body("triage_path", equalTo("Coverage review claim"))
            .body("task", equalTo("Review Coverage"));
    }
}
