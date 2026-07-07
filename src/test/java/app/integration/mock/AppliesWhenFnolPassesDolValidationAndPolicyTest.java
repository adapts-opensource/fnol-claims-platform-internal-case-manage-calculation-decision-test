package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Validates Claim Initiation & Routing orchestration decision logic.
 * Ensures correct routing behavior when FNOL passes DOL validation and policy matches.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    // Mocked external I/O service interfaces
    interface DolValidationService {
        boolean validateDolCompliance(String claimId, Map<String, Object> payload);
    }

    interface PolicyLookupService {
        Map<String, Object> findPolicyByNumber(String policyNumber);
    }

    interface RoutingOrchestrationService {
        void initiateRouting(String claimId, String decision);
    }

    @Mock
    private DolValidationService dolValidationService;

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private RoutingOrchestrationService routingOrchestrationService;

    @InjectMocks
    private ClaimInitiationRoutingDecisionService decisionOrchestrator;

    private String testClaimId;
    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testClaimId = UUID.randomUUID().toString();
        testPayload = Map.of(
                "fnolId", "fnol-" + UUID.randomUUID().toString(),
                "policyNumber", "POL-TEST-001",
                "state", "NY",
                "incidentDate", "2023-10-25"
        );
    }

    @Test
    void applies_when_fnol_passes_dol_validation_and_policy_match() {
        // Arrange: DOL validation passes compliance checks
        when(dolValidationService.validateDolCompliance(anyString(), any()))
                .thenReturn(true);

        // Arrange: Policy lookup returns an active matching policy
        when(policyLookupService.findPolicyByNumber(anyString()))
                .thenReturn(Map.of(
                        "policyNumber", "POL-TEST-001",
                        "status", "ACTIVE",
                        "coverageType", "AUTO",
                        "effectiveDate", "2023-01-01"
                ));

        // Act: Evaluate routing decision orchestration
        String routingDecision = decisionOrchestrator.evaluateRoutingDecision(testClaimId, testPayload);

        // Assert: Decision matches expected routing outcome
        assertEquals("ROUTED_TO_CLAIMS_PROCESSING", routingDecision);

        // Verify external I/O interactions occur exactly as expected
        verify(dolValidationService, times(1)).validateDolCompliance(eq(testClaimId), any());
        verify(policyLookupService, times(1)).findPolicyByNumber("POL-TEST-001");
        verify(routingOrchestrationService, times(1)).initiateRouting(eq(testClaimId), eq("ROUTED_TO_CLAIMS_PROCESSING"));
    }
}
