package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class TriageRoutingAttorneyComplexTest {

    private static final String BASE_URL_ENV = "APP_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String ENDPOINT_SLUG = "/api/claim/decision";

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = System.getenv(BASE_URL_ENV) != null
                ? System.getenv(BASE_URL_ENV)
                : DEFAULT_BASE_URL;
    }

    @Test
    void standardize_triage_routing_attorney_complex() {
        String payload = """
                {
                  "cause_of_loss": "Water",
                  "damage_severity": "High",
                  "occupancy_type": "OwnerOccupied",
                  "attorney_flag": true,
                  "risk_flags": ["AttorneyRepresentation"],
                  "tenant_id": "test-tenant-001",
                  "claim_id": "claim-std-001",
                  "policy_id": "pol-std-001",
                  "claim_number": "CLM-STD-001"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(ENDPOINT_SLUG)
                .then()
                .statusCode(200)
                .body("TRIAGE_PATH", equalTo("COMPLEX"))
                .body("ASSIGNEE_POOL", equalTo("LITIGATION_SPECIALIST"))
                .body("PRIORITY_LEVEL", equalTo("High"));
    }
}
