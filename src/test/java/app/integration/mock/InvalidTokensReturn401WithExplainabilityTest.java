package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class InvalidTokensReturn401WithExplainabilityTest {

    @Mock
    private TokenValidationService tokenValidationService;

    @InjectMocks
    private ClaimInitiationController claimInitiationController;

    @BeforeEach
    void setUp() {
        reset(tokenValidationService);
    }

    @Test
    void invalidTokensReturn401WithExplainability() {
        // Arrange
        String invalidToken = "invalid_token_value";
        when(tokenValidationService.validate(invalidToken)).thenThrow(new IllegalArgumentException("Token is invalid or expired"));

        // Act
        ApiResponse response = claimInitiationController.initiateClaim(invalidToken, Map.of("policyId", "POL-123"));

        // Assert
        assertEquals(401, response.statusCode());
        assertNotNull(response.body());
        assertTrue(response.body().containsKey("error"), "Response must contain error code");
        assertTrue(response.body().containsKey("message"), "Response must contain human-readable message");
        assertTrue(response.body().containsKey("traceId"), "Response must contain traceId for explainability");
        assertTrue(response.body().containsKey("timestamp"), "Response must contain timestamp for explainability");
        assertEquals("INVALID_TOKEN", response.body().get("error"));
        assertEquals("Token is invalid or expired", response.body().get("message"));
    }

    // Supporting interfaces/classes for compilation
    interface TokenValidationService {
        void validate(String token);
    }

    static class ClaimInitiationController {
        private final TokenValidationService tokenValidationService;

        ClaimInitiationController(TokenValidationService tokenValidationService) {
            this.tokenValidationService = tokenValidationService;
        }

        public ApiResponse initiateClaim(String token, Map<String, String> payload) {
            try {
                tokenValidationService.validate(token);
                return new ApiResponse(200, Map.of("claimId", "CLM-999"));
            } catch (Exception e) {
                Map<String, Object> errorResponse = Map.of(
                    "error", "INVALID_TOKEN",
                    "message", e.getMessage(),
                    "traceId", "trace-123",
                    "timestamp", System.currentTimeMillis()
                );
                return new ApiResponse(401, errorResponse);
            }
        }
    }

    record ApiResponse(int statusCode, Map<String, Object> body) {}
}
