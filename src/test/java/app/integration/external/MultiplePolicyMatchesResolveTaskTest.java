package app.integration.external;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Multi-Channel FNOL Submission: State Transition Calculation Tests")
public class SubmitFnolMultiplePolicyMatchesResolveTaskTest {

    private static final String BASE_URL_ENV = "APP_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String ENDPOINT = "/api/multi-channel-fnol-submission/state-transition/calculation";

    @BeforeAll
    static void setUp() {
        String baseUrl = System.getenv(BASE_URL_ENV);
        RestAssured.baseURI = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : DEFAULT_BASE_URL;
    }

    @Test
    @DisplayName("submit_fnol_multiple_policy_matches_resolve_task")
    void submit_fnol_multiple_policy_matches_resolve_task() {
        String payload = """
                {
                  "policy_number": "POL-DUP-2024-001",
                  "risk_address": "200 Bay St, Tampa, FL, 33602",
                  "date_of_loss": "2024-06-10",
                  "product_form": "HO3",
                  "cause_of_loss": "Water",
                  "channel": "agent_portal"
                }
                """;

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post(ENDPOINT)
                .then()
                .statusCode(200)
                .body("state", equalTo("Intake Review"))
                .body("match_confidence_score", lessThan(0.90))
                .body("tasks", hasItem(
                        allOf(
                                hasEntry("task_type", "Resolve Policy Match"),
                                hasEntry("policy_match_status", "MultipleMatches")
                        )
                ));
    }
}
