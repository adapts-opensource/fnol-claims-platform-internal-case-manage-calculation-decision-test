package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SiuReferralRecentInceptionTest {

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv("APP_BASE_URL");
        RestAssured.baseURI = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "http://localhost:8080";
    }

    @Test
    void validate_siu_referral_recent_inception() {
        String jsonPayload = """
            {
              "id": "siu-referral-recent-inception-test",
              "payload": {
                "policy_number": "POL-HO3-004",
                "policy_inception_date": "2024-09-20",
                "loss_date": "2024-09-25",
                "cause_of_loss": "Fire",
                "severity": "High",
                "reporter_type": "Named Insured"
              }
            }
            """;

        given()
            .contentType("application/json")
            .body(jsonPayload)
        .when()
            .post("/api/claim-data-standardization-decision-validation")
        .then()
            .statusCode(200)
            .body("claim_type", equalTo("SIU referral candidate"))
            .body("tasks_generated", hasItems("SIU Referral Review", "Review FNOL"))
            .body("fraud_indicator", equalTo("Recent inception followed by loss"))
            .body("triage_note", equalTo("SIU review triggered by configurable indicators"))
            .body("policy_match_status", equalTo("Matched"));
    }
}
