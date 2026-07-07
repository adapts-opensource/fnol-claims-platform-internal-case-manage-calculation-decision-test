package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DuplicateClaimDetectionExternalTest {

    private static String baseUrl;

    @BeforeAll
    public static void setupBaseUrl() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
    }

    @Test
    public void submit_duplicate_fnol_detection() {
        String payload = """
            {
              "policyNumber": "POL-FL-11111",
              "riskAddress": "789 Palm Ave, Miami, FL 33101",
              "namedInsured": "Bob Wilson",
              "dateOfLoss": "2024-07-01",
              "causeOfLoss": "Fire",
              "channel": "agent_assisted"
            }
            """;

        given()
            .baseUri(baseUrl)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/api/fnol")
        .then()
            .statusCode(200)
            .body("matchStatus", equalTo("SINGLE_MATCH"))
            .body("deduplicationStatus", equalTo("REJECT_DUPLICATE"))
            .body("explanationId", notNullValue())
            .body("claimOpened", equalTo(false))
            .body("intakeStatus", equalTo("Duplicate Review"))
            .body("taskId", notNullValue())
            .body("taskType", equalTo("Review Potential Duplicate Claim"));
    }
}
