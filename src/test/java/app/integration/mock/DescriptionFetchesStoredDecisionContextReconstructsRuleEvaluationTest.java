package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolDecisionValidationMockTest {

    @Mock
    private DecisionContextRepository contextRepository;

    @Mock
    private RuleReconstructionEngine ruleEngine;

    @Mock
    private ExplainabilityReportFormatter reportFormatter;

    @Mock
    private DataRetentionComplianceValidator retentionValidator;

    private MultiChannelFnolDecisionValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new MultiChannelFnolDecisionValidationService(
            contextRepository,
            ruleEngine,
            reportFormatter,
            retentionValidator
        );
    }

    @Test
    void description_fetches_stored_decision_context_reconstructs_rule_evaluation_sequence_formats_explainability_report_and_ensures_data_retention_compliance() {
        // Arrange
        String contextId = "fnol-decision-ctx-789";
        Map<String, Object> storedContext = Map.of(
            "id", contextId,
            "payload", Map.of("channel", "mobile", "status", "submitted", "priority", "high")
        );
        List<String> ruleSequence = List.of("policy_coverage", "fraud_heuristic", "payout_eligibility");
        Map<String, Object> formattedReport = Map.of(
            "reportId", "exp-rpt-001",
            "status", "validated",
            "compliance", "gdpr_soc2_aligned"
        );
        boolean complianceStatus = true;

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(storedContext));
        when(ruleEngine.reconstructSequence(storedContext)).thenReturn(ruleSequence);
        when(reportFormatter.format(ruleSequence)).thenReturn(formattedReport);
        when(retentionValidator.verifyAndArchive(formattedReport)).thenReturn(complianceStatus);

        // Act
        Map<String, Object> result = validationService.processDecisionValidation(contextId);

        // Assert
        assertNotNull(result, "Result should not be null after processing");
        assertEquals(formattedReport, result, "Should return formatted explainability report");
        assertTrue(complianceStatus, "Data retention compliance must be satisfied per GDPR/SOC2 NFRs");

        // Verify interaction sequence
        verify(contextRepository, times(1)).findById(contextId);
        verify(ruleEngine, times(1)).reconstructSequence(storedContext);
        verify(reportFormatter, times(1)).format(ruleSequence);
        verify(retentionValidator, times(1)).verifyAndArchive(formattedReport);
        verifyNoMoreInteractions(contextRepository, ruleEngine, reportFormatter, retentionValidator);
    }
}
