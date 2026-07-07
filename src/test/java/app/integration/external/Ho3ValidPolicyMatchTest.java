package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class Ho3ValidPolicyMatchTest {

    private static String baseUrl;

    @BeforeAll
    static void setup() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_ho3_fnol_valid_policy_match() {
        String payload = "{\n"
                + "  \"policy_number\": \"POL-FL-12345\",\n"
                + "  \"risk_address\": \"123 Main St, Miami, FL 33101\",\n"
                + "  \"named_insured\": \"John Doe\",\n"
                + "  \"date_of_loss\": \"2024-05-15\",\n"
                + "  \"product_form_code\": \"HO3\",\n"
                + "  \"cause_of_loss\": \"Wind\",\n"
                + "  \"channel\": \"api_intake\"\n"
                + "}";

        given()
                .contentType(ContentType.JSON)
                .body(payload)
        .when()
                .post("/api/multi-channel-fnol-validation-decision")
        .then()
                .statusCode(201)
                .body("match_status", equalTo("SINGLE_MATCH"))
                .body("triage_path", equalTo("Standard property claim"))
                .body("claim_number", matchesPattern("CLM-FL01-\\d{4}-\\d{4}"))
                .body("tasks", hasItem(hasEntry("task_type", "Review FNOL")))
                .body("coverage_review_flags", hasSize(0))
                .body("claim_state", equalTo("Claim Opened"));
    }
}
