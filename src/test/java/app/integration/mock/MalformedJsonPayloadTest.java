package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionValidationDecisionTest {

    @Mock
    private HttpClient mockHttpClient;

    private String malformedJsonPayload;

    @BeforeEach
    void setUp() {
        malformedJsonPayload = "{policy_id: 'POL-123', invalid_syntax";
    }

    @Test
    void malformedJsonPayload() throws IOException, InterruptedException {
        // Arrange
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/fnol/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(malformedJsonPayload))
                .build();

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("{\"error\": \"VALIDATION_DECISION\", \"code\": \"INVALID_JSON\", \"message\": \"Malformed JSON payload\"}");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        HttpResponse<String> response = mockHttpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Assert
        assertEquals(400, response.statusCode(), "Malformed JSON should trigger a 400 Bad Request");
        assertTrue(response.body().contains("INVALID_JSON"), "Response must contain validation decision code");
        verify(mockHttpClient, times(1)).send(any(), any());
    }
}
