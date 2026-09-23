package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Multi-Channel FNOL Submission: state_transition: calculation")
public class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    private static final String FEATURE_SLUG = "multi-channel-fnol-submission";
    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    @DisplayName("submit_fnol_no_policy_match_unmatched_shell")
    void submit_fnol_no_policy_match_unmatched_shell() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "INVALID-000");

        Map<String, String> riskAddress = new HashMap<>();
        riskAddress.put("line1", "500 Unknown Rd");
        riskAddress.put("city", "Jacksonville");
        riskAddress.put("state", "FL");
        riskAddress.put("zip", "32201");
        payload.put("risk_address", riskAddress);

        payload.put("named_insured", "Bob Jones");
        payload.put("date_of_loss", "2024-07-01");
        payload.put("product_form", "HO3");
        payload.put("cause_of_loss", "Theft");
        payload.put("channel", "insured_portal");

        Response response = given()
                .contentType("application/json")
                .body(payload)
                .log().ifValidationFails()
                .when()
                .post("/api/" + FEATURE_SLUG)
                .then()
                .log().ifValidationFails()
                .extract().response();

        response.then()
                .statusCode(200)
                .body("state", equalTo("Unmatched Policy"))
                .body("match_confidence_score", equalTo(0))
                .body("policy_match_status", equalTo("NoMatch"))
                .body("task_type", anyOf(equalTo("Create Intake Shell"), equalTo("Resolve Policy Match")))
                .body("claim_number", either(isNull()).or(not(emptyString())));
    }
}
