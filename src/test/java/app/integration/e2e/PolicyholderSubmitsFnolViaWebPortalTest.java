package app.integration.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class PolicyholderSubmitsFnolViaWebPortalTest {

    private HttpClient httpClient;
    private String baseUrl;
    private String endpoint;

    @BeforeEach
    void setUp() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.baseUrl = System.getenv("APP_BASE_URL") != null
            ? System.getenv("APP_BASE_URL")
            : "http://localhost:8080";
        this.endpoint = "/api/v1/fnol/submit";
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "APP_BASE_URL", matches = "http://localhost:8080|http://localhost:[0-9]+")
    void us_fnol_01_policyholder_submits_fnol_via_web_portal() {
        // Build inputs from test-case Inputs: Policyholder experiences property damage
        // and Constants JSON sidecar sample data fixtures
        String payload = """
            {
              "claimType": "PROPERTY_DAMAGE",
              "location": "123 Main St, Anytown, USA",
              "dateOfLoss": "2024-05-20T10:00:00Z",
              "description": "Vehicle collision with stationary object",
              "policyNumber": "POL-TEST-001"
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Expected Results: Policy match accuracy >= 99% on test dataset
            assertEquals(200, response.statusCode(), "FNOL submission should succeed");

            String matchAccuracyStr = extractJsonNumber(response.body(), "policyMatchAccuracy");
            assertNotNull(matchAccuracyStr, "Policy match accuracy must be returned in the response");

            double matchAccuracy = Double.parseDouble(matchAccuracyStr);
            assertTrue(matchAccuracy >= 0.99, "Policy match accuracy must be >= 99% on test dataset");
        } catch (Exception e) {
            fail("E2E test failed due to unexpected exception: " + e.getMessage());
        }
    }

    private String extractJsonNumber(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex == -1) return null;
        int colonIndex = json.indexOf(':', keyIndex);
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length() && !" ,}\n\r".contains(String.valueOf(json.charAt(valueEnd)))) {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }
}
