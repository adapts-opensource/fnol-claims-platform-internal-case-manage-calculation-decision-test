package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

/**
 * External integration test for Claim Initiation & Routing:decision:calculation.
 * Verifies standard HO3 wind claim routing logic against the live service.
 */
public class ClaimInitiationRoutingDecisionCalculationTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void route_h03_wind_standard_claim() {
        String payload = """
                {
                  "policy_form": "HO3",
                  "cause_of_loss": "wind",
                  "severity_estimate": 4500,
                  "compliance_flags": "none",
                  "channel": "api",
                  "date_of_loss": "2024-08-15",
                  "risk_address": "123 Main St Miami FL"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
        .when()
                .post("/api/claim-initiation-routing-decision-calculation")
        .then()
                .statusCode(200)
                .body("routing_path", equalTo("standard_property_claim"))
                .body("sla_hours", equalTo(72))
                .body("tasks", hasItems("Assign Adjuster", "Schedule Inspection"))
                .body("status", equalTo("Triage -> Routed"))
                .body("handler_group", equalTo("standard_adjuster_queue"));
    }
}
