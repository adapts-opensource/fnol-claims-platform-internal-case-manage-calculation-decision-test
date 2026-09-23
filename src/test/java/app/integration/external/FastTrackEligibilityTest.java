package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FastTrackEligibilityTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void submit_fnol_fast_track_eligibility() {
        String payload = """
                {
                  "policy_number": "POL-FL-2026-11111",
                  "insured_name": "Bob Wilson",
                  "risk_address": "789 Lake View Orlando FL 32801",
                  "date_of_loss": "2026-07-10",
                  "cause_of_loss": "Theft",
                  "product_form": "HO3",
                  "severity": "Low",
                  "attorney_represented": false,
                  "pa_involved": false,
                  "aob_involved": false,
                  "siu_indicators": false,
                  "documentation_sufficient": true
                }
                """;

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/claim-initiation-routing")
                .then()
                .statusCode(201)
                .body("triage_classification", equalTo("Fast-track claim"))
                .body("workflow_state", equalTo("Claim Opened"))
                .body("fast_track_routing", equalTo(true))
                .body("tasks", everyItem(not(hasEntry("name", "SIU Referral Review"))))
                .body("tasks", everyItem(not(hasEntry("name", "Attorney Representation Review"))))
                .body("statutory_diaries", notNullValue())
                .body("acknowledgment.queued", equalTo(true))
                .body("manual_review_tasks", empty())
                .body("adjuster_assignment.queue", equalTo("fast-track"));
    }
}
