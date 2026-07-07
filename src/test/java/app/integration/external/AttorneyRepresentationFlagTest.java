package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class AttorneyRepresentationFlagTest {

    private static String baseUrl;

    @BeforeAll
    static void setup() {
        baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
    }

    @Test
    void submit_fnol_attorney_representation() {
        String payload = """
                {
                  "policy_number": "POL-FL-2026-22222",
                  "insured_name": "Alice Brown",
                  "risk_address": "321 Bay St Tampa FL 33601",
                  "date_of_loss": "2026-08-20",
                  "cause_of_loss": "Fire",
                  "product_form": "HO3",
                  "reporter_type": "Attorney",
                  "attorney_represented": true,
                  "attorney_name": "Law Firm LLC"
                }
                """;

        given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/claim-initiation-routing-orchestration-transformation")
        .then()
            .statusCode(201)
            .body("triage_classification", equalTo("Represented claim"))
            .body("workflow_state", equalTo("Claim Opened"))
            .body("tasks", hasItem("Attorney Representation Review"))
            .body("communication_restrictions", equalTo("Attorney Only"))
            .body("diary_event", equalTo("Representation document due created"))
            .body("audit_log.attorney_flag", is(true))
            .body("audit_log.reporter_type", equalTo("Attorney"))
            .body("compliance_check", notNullValue())
            .body("tasks", hasItem("Review Coverage"));
    }
}
