package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolStateTransitionCalculationMockTest {

    // Mock interface representing the external Policy Administration System (PAS) client
    private interface PasClient {
        Map<String, Object> fetchPolicyDetails(Map<String, Object> request);
    }

    @Mock
    private PasClient pasClient;

    private FnolStateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new FnolStateTransitionCalculator(pasClient);
    }

    @Test
    void system_handles_pas_timeout_gracefully() {
        // Arrange: Simulate PAS timeout to test graceful degradation
        when(pasClient.fetchPolicyDetails(anyMap()))
                .thenThrow(new SocketTimeoutException("PAS service timed out after 5000ms"));

        String submissionId = "fnol-12345";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", submissionId);
        payload.put("channel", "MOBILE");
        payload.put("status", "SUBMITTED");

        // Act & Assert: Verify graceful handling without propagating uncaught exceptions
        assertDoesNotThrow(() -> {
            Map<String, Object> result = calculator.calculateStateTransition(payload);

            // Verify state transitioned to a safe/retry state per NFR availability & operability
            assertEquals("PENDING_RETRY", result.get("nextState"), "Should transition to retry state on timeout");
            assertEquals(submissionId, result.get("id"), "Original submission ID must be preserved");
            assertTrue((Boolean) result.get("timeoutHandled"), "Graceful timeout handling flag should be true");
            assertEquals("PAS_TIMEOUT", result.get("errorContext"), "Structured error context for observability");

            // Verify external call was attempted exactly once
            verify(pasClient, times(1)).fetchPolicyDetails(anyMap());
        });
    }

    // Minimal implementation of the service under test to support isolated mock testing
    static class FnolStateTransitionCalculator {
        private final PasClient pasClient;

        FnolStateTransitionCalculator(PasClient pasClient) {
            this.pasClient = pasClient;
        }

        Map<String, Object> calculateStateTransition(Map<String, Object> payload) {
            Map<String, Object> result = new HashMap<>();
            result.put("id", payload.get("id"));
            result.put("nextState", "CALCULATING");
            result.put("timeoutHandled", false);

            try {
                // Simulate calling external PAS for policy validation/rating
                Map<String, Object> pasResponse = pasClient.fetchPolicyDetails(payload);
                result.put("nextState", "APPROVED");
            } catch (SocketTimeoutException e) {
                // Graceful handling: transition to retry state, flag timeout, preserve context
                result.put("nextState", "PENDING_RETRY");
                result.put("timeoutHandled", true);
                result.put("errorContext", "PAS_TIMEOUT");
                // NFR: observability -> structured_logging -> log warning here
            } catch (Exception e) {
                result.put("nextState", "FAILED");
                result.put("errorContext", "UNKNOWN_ERROR");
            }
            return result;
        }
    }
}
