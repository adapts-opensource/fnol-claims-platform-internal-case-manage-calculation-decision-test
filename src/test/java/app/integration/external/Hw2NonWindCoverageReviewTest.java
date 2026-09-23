package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class Hw2NonWindCoverageReviewTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void submit_hw2_fnol_non_wind_coverage_review() {
        String payload = """
            {
              "policy_number": "POL-FL-67890",
              "risk_address": "456 Ocean Dr, Miami, FL 33139",
              "named_insured": "Jane Smith",
              "date_of_loss": "2024-06-10",
              "product_form_code": "HW2",
              "cause_of_loss": "Water Damage",
              "channel": "insured_portal"
            }
            """;

        Response response = given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/fnol-submission/validation/decision")
                .then()
                .statusCode(201)
                .extract().response();

        response.then()
                .body("match_status", equalTo("SINGLE_MATCH"))
                .body("coverage_flags", hasItem("NON_WIND_PERIL_EXCLUDED"))
                .body("triage_path", equalTo("Coverage review claim"))
                .body("tasks", hasItem(has("name", equalTo("Review Coverage"))))
                .body("claim_state", equalTo("Coverage Triage"))
                .body("acknowledgment_status", equalTo("pending"));
    }
}
