package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.restassured.http.ContentType;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReserveCalcDeskAdjusterFailTest {

    private static final String BASE_URL_ENV = "APP_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String FEATURE_SLUG = "internal-case-management-calculation-decision";

    private static String baseUri;

    @BeforeAll
    static void setUp() {
        baseUri = System.getenv(BASE_URL_ENV) != null ? System.getenv(BASE_URL_ENV) : DEFAULT_BASE_URL;
    }

    @Test
    void calculate_reserve_desk_adjuster_exceeds_authority() {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("user_role", "Desk Adjuster");
        requestBody.put("product_form", "HO3");
        requestBody.put("coverage_type", "Building");
        requestBody.put("cause_of_loss", "Water");
        requestBody.put("damage_type", "Flooring");
        requestBody.put("severity_score", 85);
        requestBody.put("property_characteristics", "Block");
        requestBody.put("catastrophe_event", null);
        requestBody.put("prior_claims", 2);
        requestBody.put("photos_uploaded", true);
        requestBody.put("loss_of_use", true);
        requestBody.put("contents_damage", true);
        requestBody.put("litigation_indicator", false);
        requestBody.put("claim_id", "CLM-1002");

        given()
                .baseUri(baseUri)
                .contentType(ContentType.JSON)
                .body(requestBody)
        .when()
                .post("/api/" + FEATURE_SLUG)
        .then()
                .statusCode(200)
                .body("reserve_amount", greaterThan(0))
                .body("status", equalTo("RESERVE_PENDING_APPROVAL"))
                .body("approval_task_id", notNullValue())
                .body("task_type", equalTo("Large Loss Escalation"))
                .body("audit_log", hasItem(hasEntry("action", "RESERVE_PENDING_APPROVAL")))
                .body("audit_log", hasItem(hasEntry("user_role", "Desk Adjuster")))
                .body("audit_log", hasItem(hasEntry("authority_breach", true)));
    }
}
