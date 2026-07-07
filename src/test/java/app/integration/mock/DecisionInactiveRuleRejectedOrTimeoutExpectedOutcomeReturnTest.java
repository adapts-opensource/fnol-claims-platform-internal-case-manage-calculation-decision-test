package app.integration.mock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
// JUnit 5 test class with @Test methods
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionEnrichmentMockTest {

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private DecisionEnrichmentService decisionEnrichmentService;

    @BeforeEach
    void setUp() {
        decisionEnrichmentService = new DecisionEnrichmentService(rulesEngineDecisionService, auditDiaryStore);
    }

    @Test
    void decisionInactiveRuleRejectedOrTimeoutExpectedOutcomeReturnToDraft() {
        // Given: Claim data with decision status 'Inactive' and rule condition 'Rejected or timeout'
        String claimId = "CLM-10293";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("decision", "Inactive");
        inputPayload.put("ruleCondition", "Rejected or timeout");

        ClaimDataStandardizationCalculationTransform transform = new ClaimDataStandardizationCalculationTransform();
        transform.setId(claimId);
        transform.setPayload(inputPayload);

        // Mock external I/O: DynamoDB rule lookup returns inactive rule metadata
        when(rulesEngineDecisionService.fetchRule(anyString())).thenReturn(new RuleMetadata("RULE-789", false, "REJECTED_OR_TIMEOUT"));

        // Mock external I/O: S3 audit diary write
        doNothing().when(auditDiaryStore).storeAuditEvent(anyString(), any(Map.class));

        // When: Enrichment service processes the claim data
        ClaimDataStandardizationCalculationTransform result = decisionEnrichmentService.enrich(transform);

        // Then: Verify expected outcome is mapped to 'Return to draft'
        assertEquals("RETURN_TO_DRAFT", result.getPayload().get("expectedOutcome"));

        // Verify external I/O contracts were invoked exactly once
        verify(rulesEngineDecisionService, times(1)).fetchRule(eq(claimId));
        verify(auditDiaryStore, times(1)).storeAuditEvent(eq(claimId), any(Map.class));
    }
}
