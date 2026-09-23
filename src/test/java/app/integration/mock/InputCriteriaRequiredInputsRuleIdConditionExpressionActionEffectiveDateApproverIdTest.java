package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionEnrichmentTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimDataStandardizationDecisionEnrichmentTest.class);

    @Mock
    private RuleEngineParser ruleEngineParser;

    @Mock
    private ApprovalWorkflowService approvalWorkflowService;

    @Mock
    private VersionedRuleRepository versionedRuleRepository;

    @InjectMocks
    private DecisionEnrichmentService decisionEnrichmentService;

    private Map<String, Object> validPayload;
    private Map<String, Object> invalidPayload;

    @BeforeEach
    void setUp() {
        validPayload = Map.of(
            "rule_id", "RUL_001",
            "condition_expression", "claim_amount > 1000",
            "action", "APPROVE",
            "effective_date", Instant.now().plusSeconds(3600).toString(),
            "approver_id", "USR_ADMIN_01",
            "description", "Update threshold",
            "rollback_version", "v1.0",
            "testing_results", "passed"
        );
        invalidPayload = Map.of(
            "rule_id", "RUL_002",
            "condition_expression", "claim_amount >",
            "action", "APPROVE",
            "effective_date", Instant.now().minusSeconds(3600).toString(),
            "approver_id", "USR_ADMIN_02"
        );
    }

    @Test
    void inputCriteriaRequiredInputsRuleIdConditionExpressionActionEffectiveDateApproverId() {
        LOG.info("Testing required input criteria validation and routing");
        when(ruleEngineParser.validateSyntax(anyString())).thenReturn(true);
        when(approvalWorkflowService.routeForApproval(anyString(), anyString())).thenReturn(true);
        when(versionedRuleRepository.createDraft(anyMap())).thenReturn("VER_001_DRAFT");

        var result = decisionEnrichmentService.processDecisionEnrichment(validPayload);

        assertNotNull(result);
        assertEquals("VER_001_DRAFT", result.get("version_id"));
        assertEquals("DRAFT", result.get("deployment_status"));
        verify(ruleEngineParser).validateSyntax("claim_amount > 1000");
        verify(approvalWorkflowService).routeForApproval("USR_ADMIN_01", "RUL_001");
        verify(versionedRuleRepository).createDraft(anyMap());
    }

    @Test
    void invalidSyntaxGivenMalformedConditionWhenSubmittedThenValidationErrorDraftRejected() {
        LOG.info("Testing syntax validation rejection for malformed condition");
        when(ruleEngineParser.validateSyntax(anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
            decisionEnrichmentService.processDecisionEnrichment(invalidPayload)
        );
        verify(ruleEngineParser).validateSyntax("claim_amount >");
        verifyNoInteractions(approvalWorkflowService, versionedRuleRepository);
    }

    @Test
    void effectiveDateInPastNegativeScenarioGivenInvalidDateWhenSubmittedThenValidationFails() {
        LOG.info("Testing effective date freshness validation");
        var pastDatePayload = Map.of(
            "rule_id", "RUL_003",
            "condition_expression", "severity == HIGH",
            "action", "ESCALATE",
            "effective_date", Instant.now().minusSeconds(86400).toString(),
            "approver_id", "USR_ADMIN_03"
        );
        when(ruleEngineParser.validateSyntax(anyString())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
            decisionEnrichmentService.processDecisionEnrichment(pastDatePayload)
        );
        verify(ruleEngineParser).validateSyntax("severity == HIGH");
        verifyNoInteractions(approvalWorkflowService, versionedRuleRepository);
    }

    @Test
    void validRuleUpdateGivenNewThresholdSyntaxValidApprovedWhenDeployedThenActiveVersionUpdatedOldDeprecated() {
        LOG.info("Testing full deployment lifecycle with deprecation of old version");
        var approvedPayload = Map.of(
            "rule_id", "RUL_004",
            "condition_expression", "premium > 500",
            "action", "RENEW",
            "effective_date", Instant.now().plusSeconds(1800).toString(),
            "approver_id", "USR_ADMIN_04",
            "testing_results", "passed"
        );
        when(ruleEngineParser.validateSyntax(anyString())).thenReturn(true);
        when(approvalWorkflowService.routeForApproval(anyString(), anyString())).thenReturn(true);
        when(versionedRuleRepository.createDraft(anyMap())).thenReturn("VER_004_DRAFT");
        when(versionedRuleRepository.deployAndActivate(anyString(), anyString())).thenReturn("VER_004_ACTIVE");
        when(versionedRuleRepository.deprecatePrevious(anyString())).thenReturn(true);

        var result = decisionEnrichmentService.processDecisionEnrichment(approvedPayload);

        assertEquals("VER_004_ACTIVE", result.get("deployment_status"));
        verify(versionedRuleRepository).deployAndActivate("VER_004_DRAFT", "effective_date");
        verify(versionedRuleRepository).deprecatePrevious("RUL_004");
    }

    // Supporting dependencies for compilation
    interface RuleEngineParser {
        boolean validateSyntax(String expression);
    }

    interface ApprovalWorkflowService {
        boolean routeForApproval(String approverId, String ruleId);
    }

    interface VersionedRuleRepository {
        String createDraft(Map<String, Object> payload);
        String deployAndActivate(String versionId, String effectiveDate);
        boolean deprecatePrevious(String ruleId);
    }

    class DecisionEnrichmentService {
        private final RuleEngineParser ruleEngineParser;
        private final ApprovalWorkflowService approvalWorkflowService;
        private final VersionedRuleRepository versionedRuleRepository;

        DecisionEnrichmentService(RuleEngineParser ruleEngineParser, ApprovalWorkflowService approvalWorkflowService, VersionedRuleRepository versionedRuleRepository) {
            this.ruleEngineParser = ruleEngineParser;
            this.approvalWorkflowService = approvalWorkflowService;
            this.versionedRuleRepository = versionedRuleRepository;
        }

        Map<String, Object> processDecisionEnrichment(Map<String, Object> payload) {
            String conditionExpression = (String) payload.get("condition_expression");
            String effectiveDateStr = (String) payload.get("effective_date");
            String approverId = (String) payload.get("approver_id");
            String ruleId = (String) payload.get("rule_id");

            if (!ruleEngineParser.validateSyntax(conditionExpression)) {
                throw new IllegalArgumentException("Syntax validation failed for condition: " + conditionExpression);
            }

            if (Instant.parse(effectiveDateStr).isBefore(Instant.now())) {
                throw new IllegalArgumentException("Effective date must be in the future or immediate");
            }

            if (!approvalWorkflowService.routeForApproval(approverId, ruleId)) {
                throw new IllegalArgumentException("Approval routing failed");
            }

            String draftId = versionedRuleRepository.createDraft(payload);
            String status = versionedRuleRepository.deployAndActivate(draftId, effectiveDateStr);
            versionedRuleRepository.deprecatePrevious(ruleId);
            return Map.of("version_id", draftId, "deployment_status", status);
        }
    }
}
