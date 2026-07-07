package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingOrchestrationDecisionTest {

    @Mock
    private PolicyCandidateRetrievalService policyCandidateRetrievalService;
    @Mock
    private RuleValidationService ruleValidationService;
    @Mock
    private FnolUpdateService fnolUpdateService;
    private OrchestrationDecisionService orchestrationDecisionService;

    @BeforeEach
    void setUp() {
        orchestrationDecisionService = new OrchestrationDecisionService(
                policyCandidateRetrievalService,
                ruleValidationService,
                fnolUpdateService
        );
    }

    @Test
    void description_presents_policy_candidates_with_key_details_validates_selection_against_dol_and_occupancy_rules_updates_fnol_with_selected_policy() {
        // Arrange
        String fnolId = "fnol-12345";
        String claimId = "claim-67890";
        String selectedPolicyId = "policy-abc-123";

        List<Map<String, Object>> policyCandidates = Arrays.asList(
                Map.of("id", selectedPolicyId, "type", "AUTO", "coverageLevel", "COMPREHENSIVE", "premium", 1500.00),
                Map.of("id", "policy-def-456", "type", "AUTO", "coverageLevel", "BASIC", "premium", 900.00)
        );

        Map<String, Object> selectedPolicy = policyCandidates.get(0);

        when(policyCandidateRetrievalService.getPoliciesForFnol(fnolId)).thenReturn(policyCandidates);
        when(ruleValidationService.validateDolAndOccupancy(selectedPolicy, fnolId)).thenReturn(true);

        // Act
        Map<String, Object> decisionContext = orchestrationDecisionService.evaluateAndRoute(fnolId, claimId);

        // Assert
        assertNotNull(decisionContext, "Decision context should not be null");
        assertEquals(selectedPolicyId, decisionContext.get("selectedPolicyId"), "Should select the validated policy");
        assertTrue((Boolean) decisionContext.get("rulesValidated"), "DOL and occupancy rules should be validated");

        verify(policyCandidateRetrievalService, times(1)).getPoliciesForFnol(fnolId);
        verify(ruleValidationService, times(1)).validateDolAndOccupancy(selectedPolicy, fnolId);
        verify(fnolUpdateService, times(1)).updateFnolWithSelectedPolicy(fnolId, selectedPolicy);
    }
}

// Supporting interfaces/classes for compilation completeness in isolated test context
interface PolicyCandidateRetrievalService {
    List<Map<String, Object>> getPoliciesForFnol(String fnolId);
}

interface RuleValidationService {
    boolean validateDolAndOccupancy(Map<String, Object> policy, String fnolId);
}

interface FnolUpdateService {
    void updateFnolWithSelectedPolicy(String fnolId, Map<String, Object> policy);
}

class OrchestrationDecisionService {
    private final PolicyCandidateRetrievalService policyCandidateRetrievalService;
    private final RuleValidationService ruleValidationService;
    private final FnolUpdateService fnolUpdateService;

    public OrchestrationDecisionService(PolicyCandidateRetrievalService policyCandidateRetrievalService,
                                        RuleValidationService ruleValidationService,
                                        FnolUpdateService fnolUpdateService) {
        this.policyCandidateRetrievalService = policyCandidateRetrievalService;
        this.ruleValidationService = ruleValidationService;
        this.fnolUpdateService = fnolUpdateService;
    }

    public Map<String, Object> evaluateAndRoute(String fnolId, String claimId) {
        List<Map<String, Object>> candidates = policyCandidateRetrievalService.getPoliciesForFnol(fnolId);
        Map<String, Object> selected = candidates.get(0);
        boolean rulesValid = ruleValidationService.validateDolAndOccupancy(selected, fnolId);
        if (rulesValid) {
            fnolUpdateService.updateFnolWithSelectedPolicy(fnolId, selected);
            return Map.of("selectedPolicyId", selected.get("id"), "rulesValidated", true);
        }
        throw new IllegalStateException("DOL or occupancy validation failed");
    }
}
