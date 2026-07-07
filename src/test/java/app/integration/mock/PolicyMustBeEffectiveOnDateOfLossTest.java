package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

interface PolicyReferenceService {
    LocalDate getEffectiveDate(String policyId);
}

class ClaimDecisionOrchestrator {
    private final PolicyReferenceService policyService;

    ClaimDecisionOrchestrator(PolicyReferenceService policyService) {
        this.policyService = policyService;
    }

    void validatePolicyEffectiveness(Map<String, Object> payload) {
        String policyId = (String) payload.get("policyId");
        String dateOfLossStr = (String) payload.get("dateOfLoss");
        LocalDate dateOfLoss = LocalDate.parse(dateOfLossStr);
        LocalDate effectiveDate = policyService.getEffectiveDate(policyId);

        if (effectiveDate.isAfter(dateOfLoss)) {
            throw new IllegalArgumentException("Policy must be effective on Date of Loss.");
        }
    }
}

@ExtendWith(MockitoExtension.class)
class PolicyMustBeEffectiveOnDateOfLossTest {

    @Mock
    private PolicyReferenceService mockPolicyService;

    @InjectMocks
    private ClaimDecisionOrchestrator decisionOrchestrator;

    @Test
    void policy_must_be_effective_on_date_of_loss() {
        String policyId = "POL-1001";
        LocalDate dateOfLoss = LocalDate.of(2023, 9, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);

        Map<String, Object> payload = Map.of(
            "policyId", policyId,
            "dateOfLoss", dateOfLoss.toString()
        );

        when(mockPolicyService.getEffectiveDate(policyId)).thenReturn(effectiveDate);

        assertDoesNotThrow(() -> decisionOrchestrator.validatePolicyEffectiveness(payload));
        verify(mockPolicyService, times(1)).getEffectiveDate(policyId);
    }
}
