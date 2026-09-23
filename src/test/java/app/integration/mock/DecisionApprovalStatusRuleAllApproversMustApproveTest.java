package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DecisionApprovalStatusRuleAllApproversMustApproveTest {

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private PolicyValidationService policyValidationService;

    private DecisionValidationService decisionValidationService;

    @BeforeEach
    void setUp() {
        decisionValidationService = new DecisionValidationService(rulesEngineService, policyValidationService);
    }

    @Test
    void decision_approval_status_rule_all_approvers_must_approve_expected_outcome_version_activated() {
        // Given: Rule configuration for approval status decision
        when(rulesEngineService.getRuleConfig(anyString())).thenReturn(Map.of(
                "ruleName", "All approvers must approve",
                "decisionType", "Approval status"
        ));

        // Given: All approvers have approved
        List<Map<String, String>> approverStatuses = List.of(
                Map.of("approverId", "auth1", "status", "APPROVED"),
                Map.of("approverId", "auth2", "status", "APPROVED")
        );
        when(policyValidationService.fetchApprovals(anyString())).thenReturn(approverStatuses);

        // When: Evaluating the decision for the claim payload
        Map<String, Object> claimPayload = Map.of("claimId", "CLM-001", "status", "PENDING");
        String result = decisionValidationService.evaluateApprovalDecision(claimPayload);

        // Then: Outcome matches expected version activation
        assertEquals("Version activated", result);
    }

    // Minimal interfaces representing external service contracts
    interface RulesEngineService {
        Map<String, String> getRuleConfig(String ruleId);
    }

    interface PolicyValidationService {
        List<Map<String, String>> fetchApprovals(String claimId);
    }

    // Service under test implementing the decision logic
    class DecisionValidationService {
        private final RulesEngineService rulesEngineService;
        private final PolicyValidationService policyValidationService;

        DecisionValidationService(RulesEngineService rulesEngineService, PolicyValidationService policyValidationService) {
            this.rulesEngineService = rulesEngineService;
            this.policyValidationService = policyValidationService;
        }

        String evaluateApprovalDecision(Map<String, Object> payload) {
            Map<String, String> ruleConfig = rulesEngineService.getRuleConfig("decision_approval_status");
            String decisionType = ruleConfig.get("decisionType");

            if ("Approval status".equals(decisionType)) {
                String claimId = (String) payload.get("claimId");
                List<Map<String, String>> approvals = policyValidationService.fetchApprovals(claimId);
                boolean allApproved = approvals.stream().allMatch(a -> "APPROVED".equals(a.get("status")));

                return allApproved ? "Version activated" : "Pending review";
            }
            return "Unknown";
        }
    }
}
