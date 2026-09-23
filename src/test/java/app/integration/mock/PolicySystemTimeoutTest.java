package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private PolicySystemClient policySystemClient;

    @InjectMocks
    private FnolOrchestrationService fnolOrchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void policy_system_timeout() {
        // Arrange: Simulate policy system timeout scenario
        String policyId = "POL-12345";
        Map<String, Object> fnolPayload = Map.of("policyId", policyId, "claimType", "AUTO");

        when(policySystemClient.validatePolicy(eq(policyId), anyMap()))
                .thenThrow(new SocketTimeoutException("Policy system timeout"));

        // Act & Assert: Verify orchestration/validation handles timeout gracefully
        OrchestrationValidationException exception = assertThrows(
                OrchestrationValidationException.class,
                () -> fnolOrchestrationService.submitAndValidate(fnolPayload)
        );

        assertTrue(exception.getMessage().contains("Policy system timeout"),
                "Exception should propagate or wrap the timeout cause");
        assertTrue(exception.getCause() instanceof SocketTimeoutException,
                "Root cause should be the simulated timeout exception");

        // Verify policy system was called exactly once
        verify(policySystemClient, times(1)).validatePolicy(eq(policyId), anyMap());
    }

    // Supporting interfaces and classes for mock orchestration/validation testing
    interface PolicySystemClient {
        Map<String, Object> validatePolicy(String policyId, Map<String, Object> claimPayload);
    }

    class FnolOrchestrationService {
        private final PolicySystemClient policySystemClient;

        public FnolOrchestrationService(PolicySystemClient policySystemClient) {
            this.policySystemClient = policySystemClient;
        }

        public Map<String, Object> submitAndValidate(Map<String, Object> fnolPayload) {
            String policyId = (String) fnolPayload.get("policyId");
            Map<String, Object> validation = policySystemClient.validatePolicy(policyId, fnolPayload);
            return Map.of("status", "VALIDATED", "payload", validation);
        }
    }

    class OrchestrationValidationException extends RuntimeException {
        public OrchestrationValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
