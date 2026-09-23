package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class ChannelNormalizationConsistencyTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    // Maps to POST /api/fnol/submit as defined in contracts/endpoints.json for this feature
    private static final String FNOL_SUBMIT_ENDPOINT = "/api/fnol/submit";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void normalize_agent_vs_api_fnol_workflow() {
        String agentPayload = "{"
                + "\"channel\":\"agent_portal\","
                + "\"product\":\"HO3\","
                + "\"policy_number\":\"POL-FL-2024-002\","
                + "\"date_of_loss\":\"2024-05-20\","
                + "\"cause_of_loss\":\"wind\","
                + "\"insured_name\":\"Jane Doe\","
                + "\"risk_address\":\"200 Beach Blvd\","
                + "\"reporter_type\":\"agent\","
                + "\"tenant_id\":\"NewCo\""
                + "}";

        String apiPayload = agentPayload.replace("\"channel\":\"agent_portal\"", "\"channel\":\"api\"");

        given()
                .contentType(ContentType.JSON)
                .body(agentPayload)
                .when()
                .post(FNOL_SUBMIT_ENDPOINT)
                .then()
                .statusCode(201)
                .body("claim_number", notNullValue())
                .body("triage.outcome", equalTo(apiTriagedOutcome()))
                .body("tasks", hasItems("Review FNOL", "Acknowledge Claim", "Assign Adjuster"))
                .body("policy_id", notNullValue())
                .body("tenant_id", equalTo("NewCo"))
                .body("audit.channel", equalTo("agent_portal"))
                .body("insured_name", equalTo("Jane Doe"))
                .body("risk_address", equalTo("200 Beach Blvd"))
                .body("date_of_loss", equalTo("2024-05-20"))
                .extract().response();

        given()
                .contentType(ContentType.JSON)
                .body(apiPayload)
                .when()
                .post(FNOL_SUBMIT_ENDPOINT)
                .then()
                .statusCode(201)
                .body("claim_number", notNullValue())
                .body("triage.outcome", equalTo(apiTriagedOutcome()))
                .body("tasks", hasItems("Review FNOL", "Acknowledge Claim", "Assign Adjuster"))
                .body("policy_id", notNullValue())
                .body("tenant_id", equalTo("NewCo"))
                .body("audit.channel", equalTo("api"))
                .body("insured_name", equalTo("Jane Doe"))
                .body("risk_address", equalTo("200 Beach Blvd"))
                .body("date_of_loss", equalTo("2024-05-20"));
    }

    private String apiTriagedOutcome() {
        return "APPROVED_FOR_ADJUSTMENT";
    }
}
