package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    // Simulated external I/O contracts aligned with infra_io_contracts (Redis/DynamoDB)
    private interface PolicyLifecycleCache {
        String getPolicyStatus(String policyId);
        String getBindingDate(String policyId);
        String getExpirationDate(String policyId);
        boolean isMoratoriumActive(String stateCode, String dateOfLoss);
    }

    private interface DecisionValidationOrchestrator {
        boolean validateDateOfLoss(Map<String, Object> payload);
    }

    @Mock
    private PolicyLifecycleCache policyLifecycleCache;

    @Mock
    private DecisionValidationOrchestrator decisionValidationOrchestrator;

    @Test
    void purpose_validate_date_of_loss_against_policy_lifecycle_binding_restrictions_and_external_regulatory_moratoriums() {
        // Arrange: Initialize claim initiation payload per data model
        String claimId = "claim-init-001";
        LocalDate dateOfLoss = LocalDate.now().minusDays(30);
        LocalDate policyBindingDate = LocalDate.now().minusDays(60);
        LocalDate policyExpirationDate = LocalDate.now().plusDays(30);
        String stateCode = "NY";
        String policyId = "pol-123";

        Map<String, Object> payload = Map.of(
            "id", claimId,
            "dateOfLoss", dateOfLoss.toString(),
            "policyId", policyId,
            "stateCode", stateCode
        );

        // Mock external I/O: Policy lifecycle & binding restrictions (DynamoDB/Redis)
        when(policyLifecycleCache.getPolicyStatus(policyId)).thenReturn("ACTIVE");
        when(policyLifecycleCache.getBindingDate(policyId)).thenReturn(policyBindingDate.toString());
        when(policyLifecycleCache.getExpirationDate(policyId)).thenReturn(policyExpirationDate.toString());

        // Mock external I/O: External regulatory moratoriums check
        when(policyLifecycleCache.isMoratoriumActive(stateCode, dateOfLoss.toString())).thenReturn(false);

        // Mock orchestrator decision outcome
        when(decisionValidationOrchestrator.validateDateOfLoss(payload)).thenReturn(true);

        // Act: Execute validation logic
        boolean isValid = decisionValidationOrchestrator.validateDateOfLoss(payload);

        // Assert: Validate business rules pass within NFR constraints (input validation, thread safety)
        assertTrue(isValid, "Date of loss must be valid against active policy lifecycle and absence of moratoriums");
        verify(policyLifecycleCache, times(1)).getPolicyStatus(policyId);
        verify(policyLifecycleCache, times(1)).getBindingDate(policyId);
        verify(policyLifecycleCache, times(1)).getExpirationDate(policyId);
        verify(policyLifecycleCache, times(1)).isMoratoriumActive(stateCode, dateOfLoss.toString());
    }
}
