package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class EnrichDecisionStandardHo3ActivePolicyTest {

    private static String baseUrl;

    @BeforeAll
    static void setUp() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void enrich_decision_standard_ho3_active_policy() {
        String correlationId = java.util.UUID.randomUUID().toString();
        String payload = """
            {
              "id": "test-claim-001",
              "payload": {
                "policy_number": "FL-HO3-2024-001",
                "risk_address": "123 Palm Ave, Miami, FL",
                "named_insured": "John Doe",
                "date_of_loss": "2024-05-10",
                "product_form": "HO3",
                "cause_of_loss": "Wind",
                "occupancy_type": "OwnerOccupied"
              }
            }
            """;

        given()
                .contentType(ContentType.JSON)
                .header("X-Correlation-Id", correlationId)
                .body(payload)
                .when()
                .post("/api/claim-data-standardization-enrichment-decision")
                .then()
                .statusCode(200)
                .body("policy_match_status", equalTo("Active"))
                .body("triage_path", equalTo("StandardPropertyClaim"))
                .body("task_queue_entries", hasItems("Review FNOL", "Acknowledge Claim"))
                .body("audit_log.correlation_id", equalTo(correlationId))
                .body("audit_log.decision_context", notNullValue());
    }
}
