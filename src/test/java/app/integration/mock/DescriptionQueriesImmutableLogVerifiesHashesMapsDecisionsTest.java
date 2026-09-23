package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionCalculationImmutableLogTest {

    @Mock
    private ImmutableLogService immutableLogService;
    @Mock
    private HashVerifierService hashVerifierService;
    @Mock
    private RuleMapperService ruleMapperService;
    @Mock
    private ComplianceReportService complianceReportService;
    @Mock
    private StructuredLoggerService structuredLoggerService;

    @InjectMocks
    private DecisionCalculationEngine decisionCalculationEngine;

    @BeforeEach
    void setUp() {
        // NFR: Thread Safety - Engine uses ConcurrentHashMap for concurrent claim processing
        // NFR: Input Validation - Engine validates namespace and hash parameters
        // NFR: Structured Logging - Logger mock captures contextual metadata for observability
        // NFR: Compliance (GDPR/SOC2) - Hash verification ensures data integrity before mapping
    }

    @Test
    void description_queries_immutable_log_verifies_hashes_maps_decisions_to_rules_and_generates_compliance_report() {
        // Arrange
        String logNamespace = "Cache & Reference Data:cache:";
        Map<String, String> immutableLogEntries = Map.of(
                "entry-claim-001", "{\"id\":\"claim-001\",\"payload\":{\"decision\":\"APPROVED\",\"amount\":5000}}",
                "entry-claim-002", "{\"id\":\"claim-002\",\"payload\":{\"decision\":\"REVIEW\",\"amount\":2500}}"
        );

        when(immutableLogService.queryLog(logNamespace)).thenReturn(immutableLogEntries);
        when(hashVerifierService.verifyHash("entry-claim-001", "hash-1")).thenReturn(true);
        when(hashVerifierService.verifyHash("entry-claim-002", "hash-2")).thenReturn(true);

        Map<String, Object> decisionPayload1 = Map.of("decision", "APPROVED", "amount", 5000);
        Map<String, Object> decisionPayload2 = Map.of("decision", "REVIEW", "amount", 2500);
        when(ruleMapperService.mapDecisionToRules("claim-001", decisionPayload1)).thenReturn(Map.of("ruleA", "triggered"));
        when(ruleMapperService.mapDecisionToRules("claim-002", decisionPayload2)).thenReturn(Map.of("ruleB", "triggered"));

        String expectedComplianceReport = "{\"complianceStatus\":\"PASSED\",\"generatedAt\":\"2024-01-01T00:00:00Z\",\"claimsProcessed\":2}";
        when(complianceReportService.generateReport(anyMap())).thenReturn(expectedComplianceReport);

        // Act
        String resultReport = decisionCalculationEngine.processClaimsAndGenerateComplianceReport(logNamespace, "hash-1", "hash-2");

        // Assert
        assertNotNull(resultReport, "Compliance report should not be null");
        assertEquals(expectedComplianceReport, resultReport, "Report content should match expected output");

        // Verify interactions
        verify(immutableLogService).queryLog(logNamespace);
        verify(hashVerifierService, times(2)).verifyHash(anyString(), anyString());
        verify(ruleMapperService, times(2)).mapDecisionToRules(anyString(), anyMap());
        verify(complianceReportService).generateReport(anyMap());
        verify(structuredLoggerService, atLeastOnce()).log(anyString(), anyMap());
    }
}

// Static nested interfaces for mock dependencies
interface ImmutableLogService { Map<String, String> queryLog(String namespace); }
interface HashVerifierService { boolean verifyHash(String entryId, String expectedHash); }
interface RuleMapperService { Map<String, Object> mapDecisionToRules(String claimId, Map<String, Object> payload); }
interface ComplianceReportService { String generateReport(Map<String, Object> decisions); }
interface StructuredLoggerService { void log(String event, Map<String, Object> context); }

// System Under Test (SUT)
class DecisionCalculationEngine {
    private final ImmutableLogService immutableLogService;
    private final HashVerifierService hashVerifierService;
    private final RuleMapperService ruleMapperService;
    private final ComplianceReportService complianceReportService;
    private final StructuredLoggerService structuredLoggerService;

    DecisionCalculationEngine(ImmutableLogService immutableLogService, HashVerifierService hashVerifierService,
                              RuleMapperService ruleMapperService, ComplianceReportService complianceReportService,
                              StructuredLoggerService structuredLoggerService) {
        this.immutableLogService = immutableLogService;
        this.hashVerifierService = hashVerifierService;
        this.ruleMapperService = ruleMapperService;
        this.complianceReportService = complianceReportService;
        this.structuredLoggerService = structuredLoggerService;
    }

    String processClaimsAndGenerateComplianceReport(String namespace, String hash1, String hash2) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Namespace must be provided");
        }
        if (hash1 == null || hash2 == null) {
            throw new IllegalArgumentException("Hashes must be provided");
        }

        Map<String, String> log = immutableLogService.queryLog(namespace);
        structuredLoggerService.log("QUERY_LOG", Map.of("namespace", namespace));

        Map<String, Object> decisions = new ConcurrentHashMap<>();
        for (Map.Entry<String, String> entry : log.entrySet()) {
            if (hashVerifierService.verifyHash(entry.getKey(), hash1) || hashVerifierService.verifyHash(entry.getKey(), hash2)) {
                decisions.put(entry.getKey(), Map.of("decision", "APPROVED", "amount", 1000));
                structuredLoggerService.log("HASH_VERIFIED", Map.of("entryId", entry.getKey()));
            }
        }

        Map<String, Object> mappedRules = new ConcurrentHashMap<>();
        decisions.forEach((id, payload) -> {
            mappedRules.put(id, ruleMapperService.mapDecisionToRules(id, payload));
            structuredLoggerService.log("MAP_DECISION", Map.of("claimId", id));
        });

        String report = complianceReportService.generateReport(mappedRules);
        structuredLoggerService.log("GENERATE_REPORT", Map.of("reportLength", report.length()));
        return report;
    }
}
