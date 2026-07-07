package app.integration.external;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DuplicateAndSiURoutingTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT = "/api/multi-channel-fnol-submission/orchestration/decision";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void submitFnolApiDuplicateDetectionSiu() {
        String payload = """
            {
              "channel": "API",
              "policy_number": "FL-HO3-112233",
              "date_of_loss": "2024-08-10",
              "cause_of_loss": "theft",
              "product": "HO3",
              "reporter": "Jane Doe",
              "risk_address": "789 Pine Rd Orlando FL 32801",
              "prior_claim_id": "CLM-FL01-2024-00001111",
              "fraud_score": 85,
              "attorney_flag": false,
              "catastrophe_code": null
            }
            """;

        Response response = given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post(ENDPOINT)
                .then()
                .statusCode(200)
                .extract()
                .response();

        response.then()
                .body("state", equalTo("Duplicate Review"))
                .body("claim_number", is(nullValue()))
                .body("tasks", hasItems("Review Potential Duplicate Claim", "SIU Referral Review"))
                .body("new_claim_opened", is(false))
                .body("audit_log", everyItem(hasEntry("rule", "duplicate_detection")));
    }
}
