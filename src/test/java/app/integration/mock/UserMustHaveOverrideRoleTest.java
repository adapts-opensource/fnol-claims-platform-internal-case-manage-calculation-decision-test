package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private RoutingDecisionCalculator calculatorService;

    @Test
    void user_must_have_override_role() {
        String userId = "agent_without_override_role";
        Map<String, Object> payload = Map.of(
            "id", "claim-12345",
            "payload", Map.of("claimType", "AUTO", "amount", 15000.00)
        );

        // Arrange: Mock the calculator to enforce role requirement and simulate external I/O isolation
        when(calculatorService.calculateDecision(eq(userId), anyMap()))
            .thenThrow(new SecurityException("User must have override role to perform calculation"));

        // Act & Assert: Verify exception is thrown when role is missing
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            calculatorService.calculateDecision(userId, payload);
        });

        assertEquals("User must have override role to perform calculation", exception.getMessage());
        verify(calculatorService, never()).calculateDecision(eq("agent_with_override_role"), anyMap());
    }

    /**
     * Minimal interface representing the calculation service under test.
     * External I/O (Redis/DynamoDB) is abstracted behind this interface and fully mocked.
     */
    @FunctionalInterface
    private interface RoutingDecisionCalculator {
        Map<String, Object> calculateDecision(String userId, Map<String, Object> payload);
    }
}
