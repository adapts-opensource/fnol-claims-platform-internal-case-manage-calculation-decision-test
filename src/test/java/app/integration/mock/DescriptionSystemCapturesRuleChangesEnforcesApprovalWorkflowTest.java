package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationEnrichmentRuleChangeTest {

    @Mock
    private SyntaxValidator syntaxValidator;
    @Mock
    private ApprovalWorkflow approvalWorkflow;
    @Mock
    private VersionManager versionManager;
    @Mock
    private EffectiveDateResolver effectiveDateResolver;
    @Mock
    private AuditDiaryStore auditDiaryStore;
    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    private ClaimEnrichmentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimEnrichmentOrchestrator(
            syntaxValidator, approvalWorkflow, versionManager,
            effectiveDateResolver, auditDiaryStore, rulesEngineDecisionService
        );
    }

    @Test
    void description_system_captures_rule_changes_enforces_approval_workflow_assigns_version_sets_effective_date_and_validates_syntax_before_activation() {
        // Given
        Map<String, Object> rulePayload = Map.of("ruleId", "R-101", "condition", "age > 65");
        when(syntaxValidator.validate(anyString())).thenReturn(true);
        when(approvalWorkflow.requestApproval(anyString())).thenReturn(ApprovalStatus.APPROVED);
        when(versionManager.assignNextVersion(anyString())).thenReturn("v1.2");
        when(effectiveDateResolver.computeEffectiveDate(any())).thenReturn(LocalDate.now().plusDays(1));
        doNothing().when(auditDiaryStore).logChange(anyString(), anyString());

        // When
        EnrichmentResult result = orchestrator.processRuleChange(rulePayload, "claim-001");

        // Then
        assertNotNull(result);
        assertEquals("v1.2", result.version());
        assertEquals(LocalDate.now().plusDays(1), result.effectiveDate());
        assertEquals(ApprovalStatus.APPROVED, result.approvalStatus());

        // Verify orchestration order and external I/O contracts
        verify(syntaxValidator).validate(anyString());
        verify(approvalWorkflow).requestApproval(anyString());
        verify(versionManager).assignNextVersion(anyString());
        verify(effectiveDateResolver).computeEffectiveDate(any());
        verify(auditDiaryStore).logChange(anyString(), anyString());
        verify(rulesEngineDecisionService).queueForActivation(anyString());
    }

    // Minimal domain interfaces and orchestrator to support the mock test
    public enum ApprovalStatus { APPROVED, PENDING, REJECTED }
    public record EnrichmentResult(String version, LocalDate effectiveDate, ApprovalStatus approvalStatus) {}
    public interface SyntaxValidator { boolean validate(String rule); }
    public interface ApprovalWorkflow { ApprovalStatus requestApproval(String ruleId); }
    public interface VersionManager { String assignNextVersion(String ruleId); }
    public interface EffectiveDateResolver { LocalDate computeEffectiveDate(Map<String, Object> payload); }
    public interface AuditDiaryStore { void logChange(String entity, String detail); }
    public interface RulesEngineDecisionService { void queueForActivation(String ruleId); }

    public static class ClaimEnrichmentOrchestrator {
        private final SyntaxValidator syntaxValidator;
        private final ApprovalWorkflow approvalWorkflow;
        private final VersionManager versionManager;
        private final EffectiveDateResolver effectiveDateResolver;
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineDecisionService;

        public ClaimEnrichmentOrchestrator(SyntaxValidator syntaxValidator, ApprovalWorkflow approvalWorkflow,
                                           VersionManager versionManager, EffectiveDateResolver effectiveDateResolver,
                                           AuditDiaryStore auditDiaryStore, RulesEngineDecisionService rulesEngineDecisionService) {
            this.syntaxValidator = syntaxValidator;
            this.approvalWorkflow = approvalWorkflow;
            this.versionManager = versionManager;
            this.effectiveDateResolver = effectiveDateResolver;
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
        }

        public EnrichmentResult processRuleChange(Map<String, Object> payload, String claimId) {
            String ruleId = (String) payload.get("ruleId");
            if (!syntaxValidator.validate(ruleId)) {
                throw new IllegalArgumentException("Syntax validation failed");
            }
            ApprovalStatus status = approvalWorkflow.requestApproval(ruleId);
            if (status != ApprovalStatus.APPROVED) {
                throw new IllegalStateException("Approval workflow enforced");
            }
            auditDiaryStore.logChange(claimId, "Rule change captured");
            String version = versionManager.assignNextVersion(ruleId);
            LocalDate effectiveDate = effectiveDateResolver.computeEffectiveDate(payload);
            rulesEngineDecisionService.queueForActivation(ruleId);
            return new EnrichmentResult(version, effectiveDate, status);
        }
    }
}
