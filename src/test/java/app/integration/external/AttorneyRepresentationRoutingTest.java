package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * External integration test for Attorney Representation Routing.
 * Verifies that FNOL submissions with attorney flags trigger correct routing,
 * task creation, and communication restrictions.
 */
public class AttorneyRepresentationRoutingTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void route_fnol_attorney_representation_flow() {
        String payload = """
            {
              "channel": "api",
              "policy_number": "POL-FL-2024-003",
              "date_of_loss": "2024-06-01",
              "cause_of_loss": "water",
              "attorney_represented": true,
              "attorney_name": "Law Firm LLP",
              "attorney_license": "FL12345",
              "reporter_type": "attorney",
              "tenant_id": "NewCo"
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/api/fnol")
        .then()
            .statusCode(201)
            .body("claim_status", equalTo("Claim Opened"))
            .body("claim_id", notNullValue())
            .body("claim_number", notNullValue())
            .body("tenant_id", equalTo("NewCo"))
            .body("claim_flag", containsString("attorney_represented"))
            .body("attorney_name", equalTo("Law Firm LLP"))
            .body("attorney_license", equalTo("FL12345"))
            .body("reporter_type", equalTo("attorney"))
            .body("tasks", hasItem("Attorney Representation Review"))
            .body("tasks", hasItem("Review FNOL"))
            .body("tasks", hasItem("Acknowledge Claim"))
            .body("communication_routing.attorney_email", notNullValue())
            .body("communication_routing.direct_insured_restricted", equalTo(true));
    }
}
