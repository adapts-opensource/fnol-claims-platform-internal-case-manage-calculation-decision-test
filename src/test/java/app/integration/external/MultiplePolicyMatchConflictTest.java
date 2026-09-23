package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class MultiChannelFnolSubmissionDecisionValidationTest {

    @BeforeAll
    static void setup() {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void handle_fnol_multiple_policy_match() {
        String payload = """
            {
                "policy_number": "",
                "risk_address": "456 Oak Ave",
                "named_insured_name": "Jane Smith",
                "date_of_loss": "2024-06-10",
                "cause_of_loss": "fire",
                "contact_method": "sms"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/multi-channel-fnol-submission")
        .then()
            .statusCode(200)
            .body("match_status", equalTo("conflict"))
            .body("routing_code", equalTo("manual_triage"))
            .body("task.type", equalTo("Resolve Policy Match"))
            .body("claim_shell.status", equalTo("Unmatched FNOL"));
    }
}
