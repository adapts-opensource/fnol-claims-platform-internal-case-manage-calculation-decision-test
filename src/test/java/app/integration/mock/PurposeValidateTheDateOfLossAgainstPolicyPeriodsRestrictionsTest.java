package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    @InjectMocks
    private ClaimDecisionTransformationService transformationService;

    @SuppressWarnings("unchecked")
    @Test
    void purposeValidateTheDateOfLossAgainstPolicyPeriodsRestrictionsAndRegulatoryMoratoriumsToDetermineCoverageEligibilityAndFlagIssues() {
        // Arrange: Prepare claim payload with date_of_loss, policy boundaries, restrictions, and moratoriums
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "CLM-2023-001");
        payload.put("date_of_loss", "2023-06-15");
        payload.put("policy_start_date", "2023-01-01");
        payload.put("policy_end_date", "2023-12-31");
        payload.put("restriction_codes", List.of("FLOOD_EXCLUSION"));
        payload.put("regulatory_moratoriums", List.of("HURRICANE_MORATORIUM_2023"));

        Map<String, Object> expectedRuleResult = Map.of("eligibility", "COVERED", "flags", List.of("DATE_WITHIN_PERIOD", "REGULATORY_MORATORIUM_ACTIVE"));

        when(rulesEngineDecisionService.evaluate(anyString(), anyMap()))
                .thenReturn(expectedRuleResult);

        // Act: Execute transformation logic
        Map<String, Object> result = transformationService.transform(payload);

        // Assert: Verify coverage eligibility determination and issue flagging
        assertNotNull(result, "Transformation result should not be null");
        assertEquals("COVERED", result.get("eligibility"), "Should determine coverage eligibility based on policy periods");
        assertTrue(((List<String>) result.get("flags")).contains("DATE_WITHIN_PERIOD"), "Should flag date within policy period");
        assertTrue(((List<String>) result.get("flags")).contains("REGULATORY_MORATORIUM_ACTIVE"), "Should flag active regulatory moratorium");

        // Verify external I/O contracts are mocked and invoked per infra contracts
        verify(auditDiaryStoreService, times(1)).storeAudit(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/{id}.json"));
        verify(rulesEngineDecisionService, times(1)).evaluate(eq("RulesEngineDecisionService_table"), anyMap());
        verify(workflowTaskRouterService, times(1)).routeTask(eq("WorkflowTaskRouter_table"), anyMap());
    }

    // Mocked Infrastructure Interfaces (S3 & DynamoDB contracts)
    interface AuditDiaryStoreService {
        void storeAudit(String bucketName, String objectKeyPattern);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> evaluate(String tableName, Map<String, Object> itemPayload);
    }

    interface WorkflowTaskRouterService {
        void routeTask(String tableName, Map<String, Object> itemPayload);
    }

    // System Under Test: Decision Transformation Engine
    static class ClaimDecisionTransformationService {
        private final AuditDiaryStoreService auditDiaryStoreService;
        private final RulesEngineDecisionService rulesEngineDecisionService;
        private final WorkflowTaskRouterService workflowTaskRouterService;

        public ClaimDecisionTransformationService(AuditDiaryStoreService auditDiaryStoreService,
                                                  RulesEngineDecisionService rulesEngineDecisionService,
                                                  WorkflowTaskRouterService workflowTaskRouterService) {
            this.auditDiaryStoreService = auditDiaryStoreService;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
            this.workflowTaskRouterService = workflowTaskRouterService;
        }

        public Map<String, Object> transform(Map<String, Object> payload) {
            String dateOfLossStr = (String) payload.get("date_of_loss");
            String policyStartStr = (String) payload.get("policy_start_date");
            String policyEndStr = (String) payload.get("policy_end_date");
            List<String> restrictions = (List<String>) payload.getOrDefault("restriction_codes", List.of());
            List<String> moratoriums = (List<String>) payload.getOrDefault("regulatory_moratoriums", List.of());

            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
            LocalDate dateOfLoss = LocalDate.parse(dateOfLossStr, fmt);
            LocalDate policyStart = LocalDate.parse(policyStartStr, fmt);
            LocalDate policyEnd = LocalDate.parse(policyEndStr, fmt);

            boolean withinPeriod = !dateOfLoss.isBefore(policyStart) && !dateOfLoss.isAfter(policyEnd);
            boolean hasRestrictions = !restrictions.isEmpty();
            boolean hasMoratorium = !moratoriums.isEmpty();

            String eligibility;
            List<String> flags = new ArrayList<>();

            if (!withinPeriod) {
                eligibility = "EXCLUDED_POLICY_PERIOD";
                flags.add("DATE_OUTSIDE_POLICY");
            } else if (hasMoratorium) {
                eligibility = "COVERED";
                flags.add("REGULATORY_MORATORIUM_ACTIVE");
                flags.add("DATE_WITHIN_PERIOD");
            } else if (hasRestrictions) {
                eligibility = "EXCLUDED_RESTRICTIONS";
                flags.add("RESTRICTION_APPLIED");
                flags.add("DATE_WITHIN_PERIOD");
            } else {
                eligibility = "COVERED";
                flags.add("DATE_WITHIN_PERIOD");
            }

            Map<String, Object> auditPayload = Map.of("eligibility", eligibility, "flags", flags);
            auditDiaryStoreService.storeAudit("AuditDiaryStore-bucket", "AuditDiaryStore/{id}.json");
            rulesEngineDecisionService.evaluate("RulesEngineDecisionService_table", auditPayload);
            workflowTaskRouterService.routeTask("WorkflowTaskRouter_table", auditPayload);

            Map<String, Object> result = new HashMap<>(auditPayload);
            result.put("flags", flags);
            return result;
        }
    }
}
