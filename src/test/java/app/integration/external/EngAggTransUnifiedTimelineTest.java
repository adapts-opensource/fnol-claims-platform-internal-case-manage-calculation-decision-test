package app.integration.external;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class EngAggTransUnifiedTimelineTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL") != null ? System.getenv("APP_BASE_URL") : "http://localhost:8080";
    private static final String ENDPOINT = "/api/insured-engagement/aggregation/transformation";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    void aggregate_insured_engagement_unified_timeline() {
        String payload = "{\"claim_id\":\"CLM-1001\",\"event_types\":[\"communication\",\"task\",\"diary\"],\"channel\":\"portal\"}";

        Response response = given()
                .contentType("application/json")
                .body(payload)
                .post(ENDPOINT)
                .then()
                .statusCode(200)
                .body("timeline", notNullValue())
                .body("timeline", hasSize(greaterThan(0)))
                .body("timeline", everyItem(hasKeys("timestamp", "source_channel", "metadata", "event_type")))
                .extract().response();

        List<String> timestamps = response.jsonPath().getList("timeline.timestamp");
        assertEquals(timestamps, timestamps.stream().sorted().collect(Collectors.toList()),
                "Timeline events must be sorted chronologically");

        long uniqueCount = timestamps.stream().distinct().count();
        assertEquals(uniqueCount, timestamps.size(), "Timeline must not contain duplicate entries");
    }
}
