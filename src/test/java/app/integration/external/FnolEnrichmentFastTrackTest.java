package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FnolEnrichmentFastTrackTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    }

    @Test
    void submit_fnol_fast_track_enrichment() {
        String payload = "{"
            + "\"policy_number\":\"POL-FL-2024-HO3-001\","
            + "\"date_of_loss\":\"2024-05-10\","
            + "\"cause_of_loss\":\"wind\","
            + "\"product_form\":\"HO3\","
            + "\"reporter_type\":\"insured\","
            + "\"reporter_name\":\"John Doe\","
            + "\"risk_address\":\"123 Palm Beach Dr\","
            + "\"severity\":\"low\","
            + "\"tenant_code\":\"FL01\","
            + "\"channel\":\"api\""
            + "}";

        given()
            .baseUri(baseUrl)
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/api/multi-channel-fnol-submission-decision-enrichment")
            .then()
            .statusCode(200)
            .body("claim_number", matchesRegex("CLM-FL01-2024-.*"))
            .body("claim_type", is("Fast-track claim"))
            .body("policy_match_status", is("Matched"))
            .body("tasks", hasItem("Review FNOL"))
            .body("deductible_calculation", notNullValue())
            .body("state", is("Claim Opened"));
    }
}
