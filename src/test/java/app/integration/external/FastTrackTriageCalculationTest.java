package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FastTrackTriageCalculationTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT = "/api/multi-channel-fnol-submission/state-transition/calculation";

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void submit_fnol_fast_track_triage_calculation() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "POL-FAST-2024-001");
        payload.put("date_of_loss", "2024-08-15");
        payload.put("product_form", "HO3");
        payload.put("cause_of_loss", "Water");
        payload.put("severity", "low");
        payload.put("attorney_represented", false);
        payload.put("public_adjuster", false);
        payload.put("aob_involved", false);
        payload.put("damage_type", "minor_contents");
        payload.put("documentation_sufficient", true);
        payload.put("channel", "insured_portal");

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post(ENDPOINT)
            .then()
            .statusCode(200)
            .body("state", equalTo("Claim Opened"))
            .body("initial_claim_type", equalTo("Fast-track claim"))
            .body("tasks", not(hasItem("SIU Referral Review")))
            .body("tasks", not(hasItem("Attorney Representation Review")))
            .body("match_confidence_score", greaterThanOrEqualTo(0.95));
    }
}
