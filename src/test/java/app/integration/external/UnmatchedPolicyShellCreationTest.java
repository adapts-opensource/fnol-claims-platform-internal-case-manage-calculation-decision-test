package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class UnmatchedPolicyShellCreationTest {

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void create_unmatched_policy_fnol_shell() {
        String payload = "{"
                + "\"channel\":\"api\","
                + "\"policy_number\":\"INVALID-00000\","
                + "\"date_of_loss\":\"2024-07-15\","
                + "\"cause_of_loss\":\"fire\","
                + "\"insured_name\":\"Bob Builder\","
                + "\"risk_address\":\"300 Unknown St\","
                + "\"reporter_type\":\"insured\","
                + "\"tenant_id\":\"NewCo\""
                + "}";

        Response response = given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(payload)
            .when()
                .post("/api/fnol")
            .then()
                .statusCode(201)
                .body("claim_number", notNullValue())
                .body("claim_number", not(emptyString()))
                .body("claim_status", equalTo("Unmatched Policy"))
                .body("tasks", hasItem("Resolve Policy Match"))
                .body("tasks", hasItem("Review FNOL"))
                .body("tasks", hasItem("Acknowledge Claim"))
                .body("acknowledgment_sent", equalTo(true))
                .body("status", equalTo("Open"))
                .body("audit_event_id", notNullValue())
                .extract().response();
    }
}
