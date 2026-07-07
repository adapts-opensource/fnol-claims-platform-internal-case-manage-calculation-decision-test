package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDecisionService claimDecisionService;

    @Test
    void if_dol_outside_policy_period_route_to_coverage_review() {
        // Arrange
        String claimId = "CLM-100";
        String dateOfLoss = "2022-06-15";
        String policyStart = "2023-01-01";
        String policyEnd = "2023-12-31";

        Map<String, Object> payload = Map.of(
                "id", claimId,
                "dateOfLoss", dateOfLoss,
                "policyPeriod", Map.of("start", policyStart, "end", policyEnd)
        );

        // Mock external policy validation to indicate DOL is outside the policy period
        when(policyValidationService.isDateOfLossWithinPeriod(claimId, dateOfLoss, policyStart))
                .thenReturn(false);

        // Act
        String routingDecision = claimDecisionService.evaluate(payload);

        // Assert
        assertEquals("coverage_review", routingDecision, "Should route to coverage review when DOL is outside policy period");
        verify(policyValidationService, times(1)).isDateOfLossWithinPeriod(claimId, dateOfLoss, policyStart);
        verifyNoInteractions(rulesEngineService);
    }
}

// Minimal service definitions for test compilation and mocking external I/O
class PolicyValidationService {
    public boolean isDateOfLossWithinPeriod(String claimId, String dateOfLoss, String policyStart) {
        return false;
    }
}

class RulesEngineService {
    public Map<String, Object> applyRules(String claimId, Map<String, Object> payload) {
        return Map.of();
    }
}

class ClaimDecisionService {
    private PolicyValidationService policyValidationService;
    private RulesEngineService rulesEngineService;

    public void setPolicyValidationService(PolicyValidationService policyValidationService) {
        this.policyValidationService = policyValidationService;
    }

    public void setRulesEngineService(RulesEngineService rulesEngineService) {
        this.rulesEngineService = rulesEngineService;
    }

    public String evaluate(Map<String, Object> payload) {
        String claimId = (String) payload.get("id");
        String dateOfLoss = (String) payload.get("dateOfLoss");
        @SuppressWarnings("unchecked")
        Map<String, String> period = (Map<String, String>) payload.get("policyPeriod");
        String policyStart = period.get("start");

        if (!policyValidationService.isDateOfLossWithinPeriod(claimId, dateOfLoss, policyStart)) {
            return "coverage_review";
        }
        return "approved";
    }
}
