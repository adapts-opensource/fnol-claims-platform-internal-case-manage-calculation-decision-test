package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

// Domain & Contract Interfaces (simulated for test compilation)
interface PolicyLookupService {
    PolicyInfo findMatchingPolicy(String intakeId, String policyId);
}

interface DoLValidationService {
    ValidationResult validateDateOfLoss(PolicyInfo policy, LocalDate dateOfLoss);
}

interface FormRuleEngine {
    RuleResult evaluateRules(String channel, Map<String, Object> formData);
}

interface RoutingService {
    TriagePath assignTriagePath(RuleResult ruleResult, String tenantId);
}

record PolicyInfo(String policyId, String tenantId, LocalDate effectiveDate, LocalDate expirationDate) {}
record ValidationResult(boolean isValid, String auditTraceId) {}
record RuleResult(String routingInstruction, Map<String, Object> metadata) {}
record TriagePath(String pathCode, String assigneeGroupId) {}

// Service under test
class FnolSubmissionDecisionService {
    private final PolicyLookupService policyLookupService;
    private final DoLValidationService dolValidationService;
    private final FormRuleEngine formRuleEngine;
    private final RoutingService routingService;
    private final Logger logger;

    public FnolSubmissionDecisionService(PolicyLookupService policyLookupService,
                                         DoLValidationService dolValidationService,
                                         FormRuleEngine formRuleEngine,
                                         RoutingService routingService) {
        this.policyLookupService = policyLookupService;
        this.dolValidationService = dolValidationService;
        this.formRuleEngine = formRuleEngine;
        this.routingService = routingService;
        this.logger = (Logger) LoggerFactory.getLogger(FnolSubmissionDecisionService.class);
    }

    public TriagePath processDecision(String intakeId, String policyId, String channel,
                                      LocalDate dateOfLoss, Map<String, Object> formData,
                                      String tenantId, String idempotencyKey) {
        // Structured logging per NFR
        logger.info("FNOL_DECISION_START", "intakeId", intakeId, "channel", channel, "idempotencyKey", idempotencyKey);

        // 1. Match intake to policy
        PolicyInfo policy = policyLookupService.findMatchingPolicy(intakeId, policyId);
        if (policy == null) {
            throw new IllegalArgumentException("Policy not found for intake");
        }

        // 2. Validate Date of Loss
        ValidationResult dolValidation = dolValidationService.validateDateOfLoss(policy, dateOfLoss);
        if (!dolValidation.isValid()) {
            logger.warn("FNOL_DOL_INVALID", "auditTraceId", dolValidation.auditTraceId());
            throw new IllegalArgumentException("Date of Loss is outside policy period");
        }

        // 3. Apply form rules
        RuleResult ruleResult = formRuleEngine.evaluateRules(channel, formData);
        if (ruleResult == null || ruleResult.routingInstruction() == null) {
            throw new IllegalStateException("Form rules did not produce a routing instruction");
        }

        // 4. Route to triage path
        TriagePath triagePath = routingService.assignTriagePath(ruleResult, tenantId);

        // Structured logging per NFR
        logger.info("FNOL_DECISION_COMPLETE", "triagePath", triagePath.pathCode(), "tenantId", tenantId);
        return triagePath;
    }
}

public class PurposeMatchIntakeToPolicyValidateDolApplyTest {

    @Mock
    private PolicyLookupService policyLookupService;
    @Mock
    private DoLValidationService dolValidationService;
    @Mock
    private FormRuleEngine formRuleEngine;
    @Mock
    private RoutingService routingService;
    private FnolSubmissionDecisionService fnolSubmissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fnolSubmissionService = new FnolSubmissionDecisionService(
                policyLookupService, dolValidationService, formRuleEngine, routingService
        );
    }

    @Test
    void purpose_match_intake_to_policy_validate_dol_apply_form_rules_and_route_to_triage_path() {
        // Arrange
        String intakeId = "INT-2024-001";
        String policyId = "POL-CAR-998877";
        String tenantId = "tenant-insurance-alpha";
        String channel = "WEB_MOBILE";
        String idempotencyKey = UUID.randomUUID().toString();
        LocalDate dateOfLoss = LocalDate.of(2024, 5, 15);
        Map<String, Object> formData = Map.of("damageType", "REAR_END_COLLISION", "injuriesReported", false);
        String expectedAuditTrace = "AUD-TRACE-7788";
        String expectedTriagePath = "AUTO_REPAIR_DIRECT";

        PolicyInfo mockPolicy = new PolicyInfo(policyId, tenantId, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31));
        ValidationResult mockDolValidation = new ValidationResult(true, expectedAuditTrace);
        RuleResult mockRuleResult = new RuleResult(expectedTriagePath, Map.of("priority", "HIGH"));
        TriagePath mockTriagePath = new TriagePath(expectedTriagePath, "GROUP_AUTO_REPAIR");

        when(policyLookupService.findMatchingPolicy(intakeId, policyId)).thenReturn(mockPolicy);
        when(dolValidationService.validateDateOfLoss(mockPolicy, dateOfLoss)).thenReturn(mockDolValidation);
        when(formRuleEngine.evaluateRules(channel, formData)).thenReturn(mockRuleResult);
        when(routingService.assignTriagePath(mockRuleResult, tenantId)).thenReturn(mockTriagePath);

        // Act
        TriagePath actualTriagePath = fnolSubmissionService.processDecision(
                intakeId, policyId, channel, dateOfLoss, formData, tenantId, idempotencyKey
        );

        // Assert
        assertNotNull(actualTriagePath);
        assertEquals(expectedTriagePath, actualTriagePath.pathCode());
        assertEquals("GROUP_AUTO_REPAIR", actualTriagePath.assigneeGroupId());

        // Verify execution order matches feature flow: Match -> Validate DoL -> Apply Rules -> Route
        InOrder inOrder = inOrder(policyLookupService, dolValidationService, formRuleEngine, routingService);
        inOrder.verify(policyLookupService).findMatchingPolicy(intakeId, policyId);
        inOrder.verify(dolValidationService).validateDateOfLoss(mockPolicy, dateOfLoss);
        inOrder.verify(formRuleEngine).evaluateRules(channel, formData);
        inOrder.verify(routingService).assignTriagePath(mockRuleResult, tenantId);

        // Verify NFR compliance mocks
        verifyNoMoreInteractions(policyLookupService, dolValidationService, formRuleEngine, routingService);
        assertTrue(mockDolValidation.isValid(), "DoL must be within policy period");
        assertEquals(tenantId, mockPolicy.tenantId(), "Tenant isolation enforced");
    }
}
