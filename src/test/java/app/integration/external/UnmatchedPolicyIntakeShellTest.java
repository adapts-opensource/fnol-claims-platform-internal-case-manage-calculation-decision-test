package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Claim Initiation & Routing:orchestration:transformation - UnmatchedPolicyIntakeShell")
class SubmitFnolUnmatchedPolicyIntakeShellTest {

    @BeforeAll
    static void setup() {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    @DisplayName("submit_fnol_unmatched_policy_intake_shell")
    void submit_fnol_unmatched_policy_intake_shell() {
        Map<String, Object> payload = Map.of(
            "policy_number", "INVALID-POLICY-001",
            "insured_name", "Jane Smith",
            "risk_address", "456 Ocean Dr Fort Lauderdale FL 33301",
            "date_of_loss", "2026-06-01",
            "cause_of_loss", "Water Damage",
            "product_form", "HO3",
            "reporter_type", "Agent"
        );

        given()
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/api/v1/claims")
        .then()
            .statusCode(201)
            .body("workflow_state", equalTo("Unmatched Policy"))
            .body("policy_match_status", equalTo("No Match"))
            .body("claim_number", notNullValue())
            .body("tasks", hasItem("Resolve Policy Match"))
            .body("tasks", hasItem("Review FNOL"))
            .body("adjuster_id", nullValue())
            .body("audit_events", hasItem(hasEntry("event_type", "intake_submission")));
    }
}
