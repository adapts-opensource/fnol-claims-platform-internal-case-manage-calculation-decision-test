package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * External integration test for Multi-Channel FNOL Submission via API.
 * Verifies standard FNOL submission flow for HO3 product including claim creation,
 * policy matching, claim number generation, task creation, and acknowledgment trigger.
 */
public class ApiFnolStandardSubmission {

    private static String baseUrl;

    private static final String PAYLOAD = "{\n" +
            "  \"channel\": \"api\",\n" +
            "  \"product\": \"HO3\",\n" +
            "  \"policyNumber\": \"POL-FL-2024-001\",\n" +
            "  \"dateOfLoss\": \"2024-05-20\",\n" +
            "  \"causeOfLoss\": \"wind\",\n" +
            "  \"insuredName\": \"John Smith\",\n" +
            "  \"riskAddress\": \"100 Ocean Dr\",\n" +
            "  \"reporterType\": \"insured\",\n" +
            "  \"tenantId\": \"NewCo\"\n" +
            "}";

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_fnol_api_standard_ho3() {
        given()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .body(PAYLOAD)
        .when()
                .post("/api/fnol")
        .then()
                .statusCode(201)
                .body("claim_id", notNullValue())
                .body("claim_number", matchesRegex("CLM-NewCo-2024-\\d+"))
                .body("claim_status", equalTo("Claim Opened"))
                .body("policyMatch", equalTo(true))
                .body("tasks", hasItem("Review FNOL"))
                .body("tasks", hasItem("Acknowledge Claim"))
                .body("tasks", hasItem("Assign Adjuster"))
                .body("acknowledgment.status", equalTo("SENT"))
                .body("auditLog", hasSize(greaterThan(0)))
                .body("auditLog", hasItem(allOf(
                        hasKey("tenant_id"),
                        hasKey("timestamp"),
                        hasValue("NewCo")
                )));
    }
}
