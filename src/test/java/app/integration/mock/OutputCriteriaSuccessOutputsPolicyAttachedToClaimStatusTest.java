package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDecisionCalculationOutputCriteriaTest {

    @Mock
    private ClaimService claimService;
    @Mock
    private PolicyService policyService;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private ComplianceService complianceService;
    @Mock
    private ValidationService validationService;

    private ClaimDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ClaimDecisionCalculator(claimService, policyService, eventPublisher, complianceService, validationService);
    }

    @Test
    void testSuccessOutputsPolicyAttachedToClaimStatusUpdatedToActive() {
        String claimId = "CLM-100";
        String policyId = "POL-200";
        when(claimService.getClaim(claimId)).thenReturn(Optional.of(Map.of("id", claimId, "status", "Unmatched")));
        when(policyService.getPolicy(policyId)).thenReturn(Optional.of(Map.of("id", policyId, "status", "Active")));

        calculator.processDecision(claimId, policyId, Map.of("reasonCode", "MATCHED"));

        verify(claimService).updateStatus(eq(claimId), eq("Active"));
        verify(eventPublisher).publish(eq("policy.override.applied"), any());
        verify(eventPublisher).publish(eq("claim.context.updated"), any());
        verify(eventPublisher).publish(eq("triage.decision.retriggered"), any());
    }

    @Test
    void testFailureOutputsOverrideRejectedTaskRemainsOpen() {
        String claimId = "CLM-101";
        when(claimService.getClaim(claimId)).thenReturn(Optional.of(Map.of("id", claimId, "status", "Unmatched")));
        when(policyService.getPolicy(any())).thenReturn(Optional.empty());

        calculator.processDecision(claimId, "POL-ERR", Map.of("reasonCode", "OVERRIDE_REQUEST"));

        verify(claimService).updateStatus(eq(claimId), eq("Open"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void testComplianceEscalationGeneratedOnFailure() {
        String claimId = "CLM-102";
        when(claimService.getClaim(claimId)).thenReturn(Optional.of(Map.of("id", claimId)));
        when(policyService.getPolicy(any())).thenReturn(Optional.empty());

        calculator.processDecision(claimId, "POL-ERR", Map.of("reasonCode", "OVERRIDE_REQUEST"));

        verify(complianceService).generateEscalation(eq(claimId), any());
    }

    @Test
    void testStatusUpdatesUnmatchedToActiveAndResolvePolicyMatchClosed() {
        String claimId1 = "CLM-A";
        String claimId2 = "CLM-B";
        when(claimService.getClaim(claimId1)).thenReturn(Optional.of(Map.of("id", claimId1, "status", "Unmatched")));
        when(claimService.getClaim(claimId2)).thenReturn(Optional.of(Map.of("id", claimId2, "status", "Resolve Policy Match")));

        calculator.processBatchDecisions(List.of(claimId1, claimId2), "POL-REF", Map.of("reasonCode", "VALID"));

        verify(claimService).updateStatus(eq(claimId1), eq("Active"));
        verify(claimService).updateStatus(eq(claimId2), eq("Closed"));
    }

    @Test
    void testUserVisibleOutputsUpdatedStatusAndJustificationVisible() {
        String claimId = "CLM-VIS";
        when(claimService.getClaim(claimId)).thenReturn(Optional.of(Map.of("id", claimId)));

        calculator.processDecision(claimId, "POL-1", Map.of("reasonCode", "AUTO_MATCH", "overrideJustification", "Auditor Approved"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(claimService).updateClaim(eq(claimId), captor.capture());
        assertTrue(captor.getValue().containsKey("userVisibleOutputs"));
        assertTrue(captor.getValue().containsKey("overrideJustificationVisibleToAuditors"));
    }

    @Test
    void testEdgeCasePolicyCancelledButDoLWithinGrace() {
        String claimId = "CLM-EDGE-1";
        when(claimService.getClaim(claimId)).thenReturn(Optional.of(Map.of("id", claimId)));
        when(policyService.getPolicy("POL-CANCELLED")).thenReturn(Optional.of(Map.of("id", "POL-CANCELLED", "status", "Cancelled", "doLWithinGrace", true)));

        calculator.processDecision(claimId, "POL-CANCELLED", Map.of("reasonCode", "GRACE_PERIOD"));

        verify(claimService).updateStatus(eq(claimId), eq("Active"));
    }

    @Test
    void testEdgeCaseEndorsementChangesAfterOverride() {
        String claimId = "CLM-EDGE-2";
        when(claimService.getClaim(claimId)).thenReturn(Optional.of(Map.of("id", claimId)));
        when(policyService.getPolicy("POL-ENDO")).thenReturn(Optional.of(Map.of("id", "POL-ENDO", "status", "Active", "endorsementChanges", true)));

        calculator.processDecision(claimId, "POL-ENDO", Map.of("reasonCode", "ENDORSEMENT_UPDATE"));

        verify(eventPublisher).publish(eq("triage.decision.retriggered"), any());
        verify(claimService).updateStatus(eq(claimId), eq("Active"));
    }

    @Test
    void testNegativeScenarioInvalidPolicyIdValidation() {
        String claimId = "CLM-NEG-1";
        when(claimService.getClaim(claimId)).thenReturn(Optional.of(Map.of("id", claimId)));
        when(validationService.validatePolicyId("INVALID-POL")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
            calculator.processDecision(claimId, "INVALID-POL", Map.of("reasonCode", "TEST"))
        );
        verify(claimService, never()).updateStatus(any(), any());
    }

    @Test
    void testNegativeScenarioMissingReasonCodeBlocked() {
        String claimId = "CLM-NEG-2";
        when(claimService.getClaim(claimId)).thenReturn(Optional.of(Map.of("id", claimId)));

        assertThrows(IllegalArgumentException.class, () ->
            calculator.processDecision(claimId, "POL-1", Map.of())
        );
        verify(claimService, never()).updateStatus(any(), any());
    }
}
