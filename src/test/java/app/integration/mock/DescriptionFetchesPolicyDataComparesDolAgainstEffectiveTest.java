package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionOrchestrationMockTest {

    // Minimal interfaces representing mocked external I/O contracts
    private interface PolicyDataStore {
        Map<String, Object> fetch(String policyId);
    }

    private interface RulesAndTriageService {
        Map<String, Object> evaluateRestrictionsAndMoratorium(String policyId, String dod);
    }

    private interface DocumentManagement {
        String resolveUri(String policyId);
    }

    private interface CoverageStatusEngine {
        Map<String, Object> calculate(Map<String, Object> context);
    }

    @Mock
    private PolicyDataStore policyStore;

    @Mock
    private RulesAndTriageService rulesService;

    @Mock
    private DocumentManagement docManagement;

    @Mock
    private CoverageStatusEngine coverageEngine;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and dependency injection
    }

    @Test
    void description_fetches_policy_data_compares_dol_against_effective_expiration_cancellation_reinstatement_rewrite_dates_applies_binding_restrictions_and_moratorium_rules_and_returns_coverage_status_with_explainable_rationale() {
        // Arrange
        String policyId = "POL-789";
        String dod = "2023-06-15";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "CLAIM-001");
        payload.put("policyId", policyId);
        payload.put("dateOfLoss", dod);

        Map<String, Object> policyData = new HashMap<>();
        policyData.put("effectiveDate", "2023-01-01");
        policyData.put("expirationDate", "2024-01-01");
        policyData.put("cancellationDate", null);
        policyData.put("reinstatementDate", null);
        policyData.put("rewriteDate", null);

        Map<String, Object> rulesContext = new HashMap<>();
        rulesContext.put("bindingRestrictions", Map.of("active", true));
        rulesContext.put("moratoriumRules", Map.of("active", false));

        // Mock external I/O: DynamoDB, S3, HTTP/Rules Service
        when(policyStore.fetch(policyId)).thenReturn(policyData);
        when(rulesService.evaluateRestrictionsAndMoratorium(policyId, dod)).thenReturn(rulesContext);
        when(docManagement.resolveUri(policyId)).thenReturn("s3://doc-bucket/POL-789.json");
        when(coverageEngine.calculate(anyMap())).thenReturn(Map.of(
                "coverageStatus", "COVERED",
                "rationale", "DoL within policy period. Restrictions applied. No moratorium active."
        ));

        // Act
        Map<String, Object> result = orchestrator.process(payload);

        // Assert: Verify coverage status and explainable rationale
        assertNotNull(result);
        assertEquals("COVERED", result.get("coverageStatus"));
        assertEquals("DoL within policy period. Restrictions applied. No moratorium active.", result.get("rationale"));

        // Verify I/O interactions and thread-safe mock state
        verify(policyStore).fetch(policyId);
        verify(rulesService).evaluateRestrictionsAndMoratorium(policyId, dod);
        verify(docManagement).resolveUri(policyId);
        verify(coverageEngine).calculate(anyMap());
        verifyNoMoreInteractions(policyStore, rulesService, docManagement, coverageEngine);
    }
}
