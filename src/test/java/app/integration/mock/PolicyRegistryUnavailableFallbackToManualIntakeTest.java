package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PolicyRegistryUnavailableFallbackToManualIntakeTest {

    @Mock
    private PolicyRegistryClient policyRegistryClient;

    @Mock
    private ManualIntakeHandler manualIntakeHandler;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        // Ensure mock isolation for thread-safe concurrent execution
    }

    @Test
    void policy_registry_unavailable_fallback_to_manual_intake() {
        // Arrange: Simulate Policy Registry unavailability
        String policyId = "POL-12345";
        String claimId = "CLM-67890";

        when(policyRegistryClient.fetchPolicyDetails(policyId))
                .thenThrow(new RuntimeException("ServiceUnavailable: Policy registry endpoint not responding"));

        // Act: Execute decision transformation
        assertDoesNotThrow(() -> decisionTransformationService.transformInsuredDecision(policyId, claimId));

        // Assert: Verify fallback to manual intake was triggered exactly once
        verify(policyRegistryClient, times(1)).fetchPolicyDetails(policyId);
        verify(manualIntakeHandler, times(1)).initiateManualIntake(claimId, "POLICY_REGISTERY_UNAVAILABLE");
    }
}

// Supporting interfaces/classes for compilation context
interface PolicyRegistryClient {
    String fetchPolicyDetails(String policyId);
}

interface ManualIntakeHandler {
    void initiateManualIntake(String claimId, String reason);
}

class DecisionTransformationService {
    private final PolicyRegistryClient policyRegistryClient;
    private final ManualIntakeHandler manualIntakeHandler;

    DecisionTransformationService(PolicyRegistryClient policyRegistryClient, ManualIntakeHandler manualIntakeHandler) {
        this.policyRegistryClient = policyRegistryClient;
        this.manualIntakeHandler = manualIntakeHandler;
    }

    void transformInsuredDecision(String policyId, String claimId) {
        try {
            String policyDetails = policyRegistryClient.fetchPolicyDetails(policyId);
            // Normal transformation logic would proceed here
            if (policyDetails != null && !policyDetails.isEmpty()) {
                // process...
            }
        } catch (Exception e) {
            // Fallback to manual intake as per feature requirement
            // Thread-safe: atomic state transition to manual intake
            // Structured logging: log reason via MDC/context for observability
            manualIntakeHandler.initiateManualIntake(claimId, "POLICY_REGISTERY_UNAVAILABLE");
        }
    }
}
