package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Unmatched Policy Orchestration External Tests")
public class UnmatchedPolicyOrchestrationTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";
    private static final String ENDPOINT = "/api/fnol-standardization";

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    @DisplayName("post_fnoL_standardization_unmatched_policy")
    void postFnoLStandardizationUnmatchedPolicy() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "INVALID-000-TEST");
        payload.put("risk_address", "123 Palm Ave Miami FL 33101");
        payload.put("date_of_loss", "2024-05-15");
        payload.put("cause_of_loss", "wind");
        payload.put("reporter_name", "Jane Smith");
        payload.put("channel_type", "agent_portal");

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .post(ENDPOINT)
                .then()
                .statusCode(200)
                .body("state", equalTo("Unmatched Policy"))
                .body("tasks", hasItem("Resolve Policy Match"))
                .body("claim_shell.status", not(equalTo("closed")));
    }
}
