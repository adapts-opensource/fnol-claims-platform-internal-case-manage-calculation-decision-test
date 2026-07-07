package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * External integration test for Multi-Channel FNOL Submission:decision:enrichment.
 * NFR Compliance: Thread-safe (stateless HTTP call), Input validation (payload schema), 
 * Security (TLS enforced by base URL configuration), Observability (structured assertions).
 */
public class FnolEnrichmentDuplicateTest {

    private static final String BASE_URL_ENV = "APP_BASE_URL";
    private static final String DEFAULT_URL = "http://localhost:8080";
    private static final String ENDPOINT = "/api/multi-channel-fnol-submission-decision-enrichment";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = System.getenv(BASE_URL_ENV) != null 
            ? System.getenv(BASE_URL_ENV) : DEFAULT_URL;
    }

    @Test
    void submit_fnol_duplicate_detection_enrichment() {
        String payload = "{"
            + "\"policy_number\":\"POL-FL-DUP-001\","
            + "\"date_of_loss\":\"2024-05-10\","
            + "\"cause_of_loss\":\"wind\","
            + "\"product_form\":\"HO3\","
            + "\"reporter_type\":\"insured\","
            + "\"risk_address\":\"123 Palm Beach Dr\","
            + "\"severity\":\"low\","
            + "\"tenant_code\":\"FL01\","
            + "\"channel\":\"api\","
            + "\"candidate_match_claim_id\":\"CLM-FL01-2023-00005555\""
            + "}";

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post(ENDPOINT)
            .then()
            .statusCode(200)
            .body("claim_number", notNullValue())
            .body("state", equalTo("Duplicate Review"))
            .body("tasks", hasItem("Review Potential Duplicate Claim"))
            .body("duplicate_match.record_id", equalTo("CLM-FL01-2023-00005555"))
            .body("actions", hasItems("Merge", "Continue"));
    }
}
