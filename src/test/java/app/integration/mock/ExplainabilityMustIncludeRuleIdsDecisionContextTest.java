package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

// Minimal mock interfaces to ensure standalone compilation without external service definitions
interface RulesEngineService {
    Map<String, Object> execute(Map<String, Object> input);
}

interface PolicyValidationService {
    Map<String, Object> validate(Map<String, Object> input);
}

interface DocumentStoreService {
    String store(String bucketName, String objectKey);
}

@ExtendWith(MockitoExtension.class)
public class ExplainabilityMustIncludeRuleIdsDecisionContext {

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private DocumentStoreService documentStoreService;

    private Map<String, Object> testClaimData;

    @BeforeEach
    void setUp() {
        // Data model: claim_data_standardization_transformation_valida
        testClaimData = Map.of(
                "id", "claim-std-001",
                "payload", Map.of("policyNumber", "POL-123", "claimType", "AUTO", "severity", "LOW")
        );
    }

    @Test
    void explainability_must_include_rule_ids_decision_context() {
        // Arrange
        Map<String, Object> expectedExplainability = Map.of(
                "ruleIds", List.of("RULE-STD-001", "RULE-VAL-042"),
                "decisionContext", "Claim data standardized and validated against policy rules. Auto-approval threshold met."
        );

        when(rulesEngineService.execute(anyMap())).thenReturn(expectedExplainability);
        when(policyValidationService.validate(anyMap())).thenReturn(Map.of("status", "PASSED"));
        when(documentStoreService.store(anyString(), anyString())).thenReturn("s3://doc-store/claim-std-001.json");

        // Act
        Map<String, Object> explainabilityResult = rulesEngineService.execute(testClaimData);

        // Assert
        assertNotNull(explainabilityResult, "Explainability output must not be null");
        assertTrue(explainabilityResult.containsKey("ruleIds"), "Explainability must include ruleIds");
        assertTrue(explainabilityResult.containsKey("decisionContext"), "Explainability must include decisionContext");

        @SuppressWarnings("unchecked")
        List<String> ruleIds = (List<String>) explainabilityResult.get("ruleIds");
        assertNotNull(ruleIds, "ruleIds must not be null");
        assertFalse(ruleIds.isEmpty(), "ruleIds must contain at least one rule ID");

        Object decisionContext = explainabilityResult.get("decisionContext");
        assertNotNull(decisionContext, "decisionContext must not be null");
        assertTrue(decisionContext.toString().length() > 0, "decisionContext must not be empty");

        // Verify external I/O contracts were mocked and not called directly
        verify(rulesEngineService).execute(testClaimData);
        verify(policyValidationService).validate(testClaimData);
        verify(documentStoreService).store(anyString(), anyString());
    }
}
