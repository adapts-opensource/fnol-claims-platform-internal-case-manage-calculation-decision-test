package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class CatEventOverrideRoutingTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null 
            ? System.getenv("APP_BASE_URL") 
            : "http://localhost:8080";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void route_catastrophe_event_override() {
        String payload = """
            {
              "policy_form": "HO3",
              "cause_of_loss": "hurricane",
              "severity_estimate": 85000,
              "compliance_flags": "cat_moratorium",
              "channel": "api",
              "date_of_loss": "2024-10-01",
              "risk_address": "789 Beach Blvd Key West FL",
              "event_name": "Hurricane Ian"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/claim-initiation-routing-decision-calculation")
            .then()
            .statusCode(200)
            .body("routing_path", equalTo("catastrophe_claim"))
            .body("sla_hours", equalTo(24))
            .body("tasks", hasItems("Catastrophe Assignment", "Emergency Mitigation Review"))
            .body("status", equalTo("Triage -> Routed"))
            .body("handler_group", equalTo("catastrophe_team"))
            .body("priority_tier", equalTo("high"));
    }
}
