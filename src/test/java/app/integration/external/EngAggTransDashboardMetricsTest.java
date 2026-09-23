package app.integration.external;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * NFR Compliance: availability: ha_multi_az | compliance: gdpr, soc2 | concurrency: thread_safety
 * observability: structured_logging | operability: nfr_section | security: tls_in_transit, least_privilege_iam,
 * secrets_management, input_validation
 */
public class InsuredEngagementAggregationTransformationTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null 
            ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String FEATURE_SLUG = "insured-engagement-tracking/aggregation/transformation";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void transform_engagement_metrics_for_dashboard() {
        String payload = """
            {
              "claim_id": "CLM-1002",
              "metric_type": "engagement_summary",
              "date_range": "2024-01-01,2024-01-31"
            }
            """;

        given()
            .contentType("application/json")
            .body(payload)
            .post("/api/" + FEATURE_SLUG)
            .then()
            .statusCode(200)
            .body("aggregated_metrics.total_communications", notNullValue())
            .body("aggregated_metrics.tasks_pending", notNullValue())
            .body("aggregated_metrics.channels_used", notNullValue())
            .body("aggregated_metrics.compliance_exceptions", notNullValue());
    }
}
