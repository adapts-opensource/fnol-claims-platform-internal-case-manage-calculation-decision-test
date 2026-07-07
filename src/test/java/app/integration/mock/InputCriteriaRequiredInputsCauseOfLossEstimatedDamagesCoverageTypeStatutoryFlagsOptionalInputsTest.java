package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class InternalCaseManagementCalculationDecisionTest {

    @Mock
    private ClassificationTaxonomyService taxonomyService;
    @Mock
    private RoutingRuleEngine routingEngine;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private CentralDataStoreClient dataStoreClient;
    @Mock
    private SecureStorageClient secureStorageClient;

    private DecisionCalculationService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new DecisionCalculationService(
            taxonomyService, routingEngine, auditLogger, dataStoreClient, secureStorageClient
        );
    }

    @Test
    void inputCriteriaRequiredInputsCauseOfLossEstimatedDamagesCoverageTypeStatutoryFlagsOptionalInputs() {
        Map<String, Object> input = Map.of(
            "cause_of_loss", "windstorm",
            "estimated_damages", 50000.0,
            "coverage_type", "HO_3",
            "statutory_flags", Set.of("CATASTROPHE_EVENT"),
            "vendor_eligibility", true,
            "litigation_indicator", false
        );

        when(taxonomyService.mapCauseToClassification("windstorm")).thenReturn(Set.of("PROPERTY_CLAIM"));
        when(routingEngine.executeRoutingRules(anyMap())).thenReturn(Map.of("routing_task", "ADJUSTER_MAIN_QUEUE"));
        when(dataStoreClient.updateStatus(anyString(), eq("CLASSIFIED_ROUTED"))).thenReturn(true);
        when(secureStorageClient.writeAuditLog(anyString(), anyMap())).thenReturn("s3://secure-storage/audit/123.json");

        Map<String, Object> result = decisionService.calculateDecision(input);

        assertEquals("STANDARD_PROPERTY_CLAIM", result.get("claimtype"));
        assertEquals("ADJUSTER_MAIN_QUEUE", result.get("routing_task"));
        assertEquals("CLASSIFIED_ROUTED", result.get("claimstatus"));
        assertTrue(result.containsKey("emitted_events"));
        verify(auditLogger).log(anyString(), eq("VERSION_2024_R1"), anyMap());
    }

    @Test
    void ambiguousClassificationRuleIfMultipleCategoriesMatchRouteToSeniorTriage() {
        Map<String, Object> input = Map.of("cause_of_loss", "multi_peril", "estimated_damages", 120000.0, "coverage_type", "HO_3");
        when(taxonomyService.mapCauseToClassification("multi_peril")).thenReturn(Set.of("PROPERTY_CLAIM", "STRUCTURAL_DAMAGE"));
        when(routingEngine.executeRoutingRules(anyMap())).thenReturn(Map.of("routing_task", "SENIOR_TRIAGE_QUEUE"));

        Map<String, Object> result = decisionService.calculateDecision(input);

        assertEquals("SENIOR_TRIAGE_QUEUE", result.get("routing_task"));
        assertEquals("TASK_ESCALATED", result.get("claimstatus"));
        verify(auditLogger).log(anyString(), anyString(), anyMap());
    }

    @Test
    void missingCauseOfLossThrowsValidationException() {
        Map<String, Object> input = Map.of("estimated_damages", 10000.0, "coverage_type", "HO_3");

        assertThrows(IllegalArgumentException.class, () -> decisionService.calculateDecision(input));
        verifyNoInteractions(routingEngine, dataStoreClient, secureStorageClient);
    }

    @Test
    void litigationIndicatorForcesAttorneyAssignment() {
        Map<String, Object> input = Map.of("cause_of_loss", "windstorm", "estimated_damages", 5000.0, "coverage_type", "HO_3", "litigation_indicator", true);
        when(taxonomyService.mapCauseToClassification("windstorm")).thenReturn(Set.of("PROPERTY_CLAIM"));
        when(routingEngine.executeRoutingRules(anyMap())).thenReturn(Map.of("routing_task", "ATTORNEY_ASSIGNMENT_QUEUE"));

        Map<String, Object> result = decisionService.calculateDecision(input);

        assertEquals("ATTORNEY_ASSIGNMENT_QUEUE", result.get("routing_task"));
        assertEquals("CLASSIFIED_ROUTED", result.get("claimstatus"));
    }

    @Test
    void auditEvidenceShowsRuleChainAndVersion() {
        Map<String, Object> input = Map.of("cause_of_loss", "windstorm", "estimated_damages", 50000.0, "coverage_type", "HO_3");
        when(taxonomyService.mapCauseToClassification("windstorm")).thenReturn(Set.of("PROPERTY_CLAIM"));
        when(routingEngine.executeRoutingRules(anyMap())).thenReturn(Map.of("routing_task", "ADJUSTER_MAIN_QUEUE"));
        when(secureStorageClient.writeAuditLog(anyString(), anyMap())).thenReturn("s3://secure-storage/audit/456.json");

        decisionService.calculateDecision(input);

        verify(auditLogger).log(anyString(), eq("VERSION_2024_R1"), argThat(map ->
            map.containsKey("rule_chain") && map.containsKey("classification_decision_log")
        ));
    }
}
