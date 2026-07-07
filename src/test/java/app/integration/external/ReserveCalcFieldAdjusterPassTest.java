package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ReserveCalcFieldAdjusterPass {

    private static String baseUrl;

    @BeforeAll
    static void setupBaseUrl() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
    }

    @Test
    void calculate_reserve_field_adjuster_within_authority() {
        String endpoint = "/api/internal-case-management/calculation/decision";

        String payload = """
            {
              "user_role": "Field Adjuster",
              "product_form": "HO3",
              "coverage_type": "Building",
              "cause_of_loss": "Wind",
              "damage_type": "Roof",
              "severity_score": 60,
              "property_characteristics": "Wood Frame",
              "catastrophe_event": null,
              "prior_claims": 0,
              "photos_uploaded": true,
              "loss_of_use": false,
              "contents_damage": false,
              "litigation_indicator": false,
              "claim_id": "CLM-1001"
            }
            """;

        given()
            .baseUri(baseUrl)
            .contentType("application/json")
            .body(payload)
            .when()
            .post(endpoint)
            .then()
            .statusCode(200)
            .body("reserve_amount", notNullValue())
            .body("status", is("RESERVE_SET"))
            .body("approval_task_id", nullValue())
            .body("audit_log", hasItem(
                allOf(
                    hasEntry("action", "RESERVE_SET"),
                    hasEntry("actor_role", "Field Adjuster")
                )
            ));
    }
}
