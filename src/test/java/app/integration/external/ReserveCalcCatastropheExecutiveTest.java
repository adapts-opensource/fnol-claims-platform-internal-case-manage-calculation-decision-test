package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class InternalCaseManagementCalculationDecisionTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT = "/api/internal-case-management/calculation/decision";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void calculate_reserve_catastrophe_executive_override() {
        String payload = """
                {
                  "user_role": "Executive",
                  "product_form": "HO3",
                  "coverage_type": "Building",
                  "cause_of_loss": "Hurricane",
                  "damage_type": "Structural",
                  "severity_score": 98,
                  "property_characteristics": "Wood Frame",
                  "catastrophe_event": "HURRICANE-2024-09",
                  "prior_claims": 0,
                  "photos_uploaded": true,
                  "loss_of_use": true,
                  "contents_damage": true,
                  "litigation_indicator": false,
                  "claim_id": "CLM-1003"
                }
                """;

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post(ENDPOINT)
                .then()
                .statusCode(200)
                .body("reserve_amount", notNullValue())
                .body("reserve_amount", greaterThan(0))
                .body("status", equalTo("RESERVE_SET"))
                .body("approval_task_id", nullValue())
                .body("catastrophe_tag", equalTo(true))
                .body("audit_log", hasItem(hasEntry(equalTo("action"), equalTo("RESERVE_SET"))))
                .body("audit_log", hasItem(hasEntry(equalTo("actor"), equalTo("Executive"))))
                .body("audit_log", hasItem(hasEntry(equalTo("catastrophe_override"), equalTo(true))));
    }
}
