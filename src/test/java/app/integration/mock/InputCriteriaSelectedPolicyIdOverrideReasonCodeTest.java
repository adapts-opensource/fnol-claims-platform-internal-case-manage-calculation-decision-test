package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimRoutingDecisionCalculationTest {

    @Mock
    private PolicyLookupService policyLookupService;
    
    @Mock
    private CredentialValidationService credentialValidationService;
    
    @Mock
    private ReasonCodeResolutionService reasonCodeResolutionService;
    
    @Mock
    private ClaimContextSnapshotService claimContextSnapshotService;

    @InjectMocks
    private RoutingDecisionCalculationEngine routingDecisionCalculationEngine;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = Map.of(
                "selectedPolicyId", "POL-98765",
                "overrideReasonCode", "OVERRIDE-CLAIM-001",
                "specialistCredentials", Set.of("CRED-ADJ-AUTO", "CRED-SME-LIAB"),
                "claimContextSnapshot", Map.of(
                        "claimType", "AUTO",
                        "severityLevel", "HIGH",
                        "incidentDate", "2024-05-10",
                        "location", "US-CA"
                )
        );
    }

    @Test
    void input_criteria_selected_policy_id_override_reason_code_specialist_credentials_claim_context_snapshot() {
        // Arrange: Mock external data lookups that would typically hit Redis/DynamoDB
        when(policyLookupService.resolvePolicyDetails("POL-98765"))
                .thenReturn(Map.of("policyStatus", "ACTIVE", "coverageTier", "PREMIUM"));
        
        when(credentialValidationService.validateSpecialistCredentials(Set.of("CRED-ADJ-AUTO", "CRED-SME-LIAB")))
                .thenReturn(true);
        
        when(reasonCodeResolutionService.resolveOverrideCode("OVERRIDE-CLAIM-001"))
                .thenReturn("EXCEPTION_ROUTING_OVERRIDE");
        
        when(claimContextSnapshotService.fetchSnapshot(Map.of("claimType", "AUTO", "severityLevel", "HIGH", "incidentDate", "2024-05-10", "location", "US-CA")))
                .thenReturn(Map.of("priorityScore", 95, "recommendedQueue", "PRIORITY_AUTO_ADJ"));

        // Act: Execute calculation with combined input criteria
        Map<String, Object> decisionResult = routingDecisionCalculationEngine.calculate(testPayload);

        // Assert: Verify routing decision output matches expected calculation logic
        assertNotNull(decisionResult, "Routing decision must not be null");
        assertEquals("PRIORITY_AUTO_ADJ", decisionResult.get("assignedQueue"));
        assertEquals(95, decisionResult.get("priorityScore"));
        assertEquals("EXCEPTION_ROUTING_OVERRIDE", decisionResult.get("resolvedOverrideReason"));
        assertTrue((Boolean) decisionResult.get("credentialsValidated"));
        assertEquals("ACTIVE", decisionResult.get("policyStatus"));

        // Verify external I/O interactions occurred exactly once
        verify(policyLookupService, times(1)).resolvePolicyDetails("POL-98765");
        verify(credentialValidationService, times(1)).validateSpecialistCredentials(Set.of("CRED-ADJ-AUTO", "CRED-SME-LIAB"));
        verify(reasonCodeResolutionService, times(1)).resolveOverrideCode("OVERRIDE-CLAIM-001");
        verify(claimContextSnapshotService, times(1)).fetchSnapshot(anyMap());
    }
}
