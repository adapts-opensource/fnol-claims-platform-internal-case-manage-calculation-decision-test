package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class UnmatchedPolicyCoverageTriageTest {

    private static String baseUrl;

    @BeforeAll
    static void setup() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_fnol_portal_unmatched_policy() {
        String payload = "{"
                + "\"channel\": \"Portal\","
                + "\"policy_number\": \"FL-DP3-000000\","
                + "\"date_of_loss\": \"2024-07-01\","
                + "\"cause_of_loss\": \"water\","
                + "\"product\": \"DP3\","
                + "\"insured_name\": \"Jane Smith\","
                + "\"risk_address\": \"456 Oak Ave Tampa FL 33602\","
                + "\"policy_status\": \"expired\","
                + "\"attorney_flag\": false,"
                + "\"catastrophe_code\": null"
                + "}";

        given()
                .contentType("application/json")
                .body(payload)
        .when()
                .post("/api/multi-channel-fnol-submission")
        .then()
                .statusCode(200)
                .body("state", equalTo("Unmatched Policy"))
                .body("claim_number", either(is(nullValue())).or(equalTo("DRAFT")))
                .body("tasks", hasItems("Resolve Policy Match", "Review Coverage"))
                .body("diary", hasItem(hasEntry("event", "Claim acknowledgment due")))
                .body("intake_shell.status", equalTo("Unmatched FNOL"));
    }
}
