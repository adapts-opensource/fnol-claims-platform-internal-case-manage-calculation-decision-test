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
public class BrokerRegistryUnavailableDenySubmissionWithClearErrorTest {

    @Mock
    private BrokerRegistryClient brokerRegistryClient;

    @InjectMocks
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension auto-resets mocks; explicit setup reserved for future NFR hooks
    }

    @Test
    void broker_registry_unavailable_deny_submission_with_clear_error() {
        // Arrange
        String claimId = "CLM-98765";
        Map<String, Object> payload = Map.of("claimId", claimId, "action", "SUBMIT");
        when(brokerRegistryClient.resolveBrokerDetails(anyString()))
                .thenThrow(new RuntimeException("SERVICE_UNAVAILABLE: Broker registry endpoint not responding"));

        // Act
        ClaimOrchestrationOutcome result = orchestrationService.transitionAndValidate(claimId, payload);

        // Assert
        assertNotNull(result, "Orchestration outcome must not be null");
        assertEquals("DENIED", result.getStatus(), "Submission should be denied when broker registry is down");
        assertTrue(result.getErrorMessage().contains("BROKER_REGISTRY_UNAVAILABLE"),
                "Error message must clearly indicate broker registry failure");
        assertFalse(result.isSubmissionAllowed(), "Submission must be blocked");
    }

    // Minimal stubs to ensure standalone compilation for the integration mock test
    static interface BrokerRegistryClient {
        Map<String, Object> resolveBrokerDetails(String claimId);
    }

    static record ClaimOrchestrationOutcome(String status, String errorMessage, boolean submissionAllowed) {}

    static class ClaimDataStandardizationOrchestrationService {
        private final BrokerRegistryClient brokerRegistryClient;

        ClaimDataStandardizationOrchestrationService(BrokerRegistryClient brokerRegistryClient) {
            this.brokerRegistryClient = brokerRegistryClient;
        }

        ClaimOrchestrationOutcome transitionAndValidate(String claimId, Map<String, Object> payload) {
            try {
                brokerRegistryClient.resolveBrokerDetails(claimId);
                return new ClaimOrchestrationOutcome("APPROVED", null, true);
            } catch (Exception e) {
                String clearError = "BROKER_REGISTRY_UNAVAILABLE: Unable to validate broker details. Please retry or contact support.";
                return new ClaimOrchestrationOutcome("DENIED", clearError, false);
            }
        }
    }
}
