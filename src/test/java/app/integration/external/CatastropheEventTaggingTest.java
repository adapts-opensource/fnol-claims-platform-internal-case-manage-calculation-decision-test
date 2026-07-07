package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class CatastropheEventTaggingTest {

    private static String baseUrl;

    @BeforeAll
    static void setup() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void tag_catastrophe_event_and_assign() {
        String payload = """
            {
              "channel": "api",
              "policy_number": "POL-FL-2024-004",
              "date_of_loss": "2024-09-28",
              "cause_of_loss": "hurricane",
              "event_code": "HURRICANE-ELIAN",
              "severity": "high",
              "tenant_id": "NewCo"
            }
            """;

        Response response = given()
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/api/fnol")
        .then()
            .statusCode(201)
            .extract()
            .response();

        // Verify claim type and event linkage
        response.then()
            .body("claim_type", equalTo("Catastrophe claim"))
            .body("event_code", equalTo("HURRICANE-ELIAN"))
            .body("severity", equalTo("high"))
            .body("tenant_id", equalTo("NewCo"));

        // Verify task assignment
        response.then()
            .body("tasks", hasItem("Catastrophe Assignment"))
            .body("tasks", hasItem("Review FNOL"))
            .body("tasks", hasItem("Acknowledge Claim"));

        // Verify diary creation
        response.then()
            .body("diary_entries", hasItem(hasProperty("type", equalTo("Catastrophe Response Deadline"))))
            .body("diary_entries", hasItem(hasProperty("trigger", equalTo("Catastrophe Event Tagging"))));

        // Verify acknowledgment content
        response.then()
            .body("acknowledgment.instructions", containsString("catastrophe"))
            .body("acknowledgment.instructions", containsString("HURRICANE-ELIAN"));

        // Verify audit trail captures event association
        response.then()
            .body("audit_trail", hasItem(containsString("HURRICANE-ELIAN")))
            .body("audit_trail", hasItem(containsString("CatastropheEventTagging")));
    }
}
