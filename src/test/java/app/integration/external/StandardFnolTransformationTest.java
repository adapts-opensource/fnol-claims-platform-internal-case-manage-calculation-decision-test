package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesRegex;

/**
 * External integration test for Claim Initiation & Routing:calculation:transformation.
 * Validates FNOL payload transformation, claim number generation, and triage routing via live HTTP.
 */
public class StandardFnolTransformationExternalTest {

    private static final String BASE_URL_ENV = "APP_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    // Mapped from contracts/endpoints.json feature slug: claim-initiation-routing/transformation
    private static final String CLAIM_TRANSFORMATION_ENDPOINT = "/api/claim-initiation-routing/transformation";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv(BASE_URL_ENV) != null
                ? System.getenv(BASE_URL_ENV)
                : DEFAULT_BASE_URL;
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void transformStandardFnolAndGenerateClaimNumber() {
        String payload = """
                {
                  "tenant_code": "FL01",
                  "policy_number": "HO3-987654",
                  "date_of_loss": "2024-05-10",
                  "cause_of_loss": "wind",
                  "product_form": "HO3",
                  "severity": "low",
                  "channel": "api"
                }
                """;

        given()
                .contentType("application/json")
                .body(payload)
        .when()
                .post(CLAIM_TRANSFORMATION_ENDPOINT)
        .then()
                .statusCode(201)
                .body("claim_number", matchesRegex("CLM-FL01-2024-\\d{4}"))
                .body("initial_claim_type", equalTo("Standard property claim"))
                .body("state", equalTo("Claim Opened"))
                .body("triage_calculation.status", equalTo("completed"))
                .log().all();
    }
}
