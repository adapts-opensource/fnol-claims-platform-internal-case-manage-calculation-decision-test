package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration tests for Claim Data Standardization:decision:transformation.
 * Validates behavior against mocked infrastructure contracts (S3, DynamoDB)
 * while ensuring NFRs like compliance (audit logging) and security (input validation) are respected.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @InjectMocks
    private ClaimDataStandardizationDecisionTransformationService transformationService;

    @Test
    @DisplayName("AppliesWhenFnolIntakeIncludesDate_of_lossAndPolicy")
    void applies_when_fnol_intake_includes_date_of_loss_and_policy_context_is_available() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> fnolIntake = new HashMap<>();
        fnolIntake.put("date_of_loss", "2023-11-01");
        
        // Policy context is available
        Map<String, Object> policyContext = Map.of("policyId", "POL-123", "effectiveDate", "2023-01-01");

        // Mocking infrastructure interactions
        when(rulesEngineDecisionService.evaluate(anyString(), anyMap())).thenReturn(Map.of("decision", "APPROVED"));
        when(auditDiaryStore.write(anyString(), anyString())).thenReturn("s3://AuditDiaryStore-bucket/" + claimId + ".json");
        when(workflowTaskRouter.route(anyString())).thenReturn("TASK-456");

        // Act
        var result = transformationService.transform(claimId, fnolIntake, policyContext);

        // Assert
        assertNotNull(result, "Result should be populated when date_of_loss and policy context are present.");
        assertEquals(claimId, result.getId());
        assertTrue(result.getPayload().containsKey("standardizedDateOfLoss"));
        assertEquals("APPROVED", result.getPayload().get("decisionOutcome"));

        // Verify NFR: Compliance/Observability via Audit Store
        verify(auditDiaryStore, times(1)).write(eq("AuditDiaryStore-bucket"), eq(claimId));
        
        // Verify Infra Contracts
        verify(rulesEngineDecisionService).evaluate(eq(claimId), anyMap());
        verify(workflowTaskRouter).route(eq(claimId));
    }
}
