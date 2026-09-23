package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationCalculationDecisionTest {

    @Mock
    private Logger auditLogger;

    @Mock
    private PolicyLifecycleValidator policyValidator;

    @Mock
    private RegulatoryComplianceChecker regulatoryChecker;

    @Mock
    private DecisionCalculationEngine decisionEngine;

    private ClaimStandardizationService standardizationService;

    @BeforeEach
    void setUp() {
        standardizationService = new ClaimStandardizationService(auditLogger, policyValidator, regulatoryChecker, decisionEngine);
    }

    @Test
    void purposeValidateLossDateAgainstPolicyLifecycleAndRegulatoryRestrictions() {
        // Arrange
        String tenantId = "newco_insurance_tenant";
        String policyId = "POL_CY2023_001";
        String claimId = "CLM_20230615_001";
        String idempotencyKey = UUID.randomUUID().toString();
        LocalDate lossDate = LocalDate.of(2023, 6, 15);
        LocalDate regulatoryDeadline = LocalDate.of(2023, 12, 31);

        when(policyValidator.isWithinLifecycle(tenantId, policyId, lossDate)).thenReturn(true);
        when(regulatoryChecker.validateAgainstRestrictions(tenantId, lossDate, regulatoryDeadline)).thenReturn(true);
        when(decisionEngine.calculateDecision(anyString(), anyString(), any(LocalDate.class), anyString())).thenReturn(DecisionOutcome.APPROVED);

        // Act
        DecisionOutcome outcome = standardizationService.processClaimDecision(tenantId, policyId, claimId, lossDate, idempotencyKey);

        // Assert
        assertEquals(DecisionOutcome.APPROVED, outcome);
        verify(policyValidator).isWithinLifecycle(tenantId, policyId, lossDate);
        verify(regulatoryChecker).validateAgainstRestrictions(tenantId, lossDate, regulatoryDeadline);
        verify(decisionEngine).calculateDecision(eq(tenantId), eq(policyId), eq(lossDate), eq(idempotencyKey));
        verify(auditLogger).info(anyString(), any(Object[].class));
    }
}

enum DecisionOutcome { APPROVED, REJECTED, PENDING }

interface PolicyLifecycleValidator { boolean isWithinLifecycle(String tenantId, String policyId, LocalDate lossDate); }
interface RegulatoryComplianceChecker { boolean validateAgainstRestrictions(String tenantId, LocalDate lossDate, LocalDate deadline); }
interface DecisionCalculationEngine { DecisionOutcome calculateDecision(String tenantId, String policyId, LocalDate lossDate, String idempotencyKey); }

class ClaimStandardizationService {
    private final Logger auditLogger;
    private final PolicyLifecycleValidator policyValidator;
    private final RegulatoryComplianceChecker regulatoryChecker;
    private final DecisionCalculationEngine decisionEngine;

    ClaimStandardizationService(Logger auditLogger, PolicyLifecycleValidator policyValidator, RegulatoryComplianceChecker regulatoryChecker, DecisionCalculationEngine decisionEngine) {
        this.auditLogger = auditLogger;
        this.policyValidator = policyValidator;
        this.regulatoryChecker = regulatoryChecker;
        this.decisionEngine = decisionEngine;
    }

    DecisionOutcome processClaimDecision(String tenantId, String policyId, String claimId, LocalDate lossDate, String idempotencyKey) {
        if (tenantId == null || policyId == null || lossDate == null || idempotencyKey == null) {
            throw new IllegalArgumentException("Input validation failed: all fields required");
        }
        boolean inLifecycle = policyValidator.isWithinLifecycle(tenantId, policyId, lossDate);
        boolean compliant = regulatoryChecker.validateAgainstRestrictions(tenantId, lossDate, LocalDate.of(2023, 12, 31));

        if (!inLifecycle || !compliant) {
            auditLogger.warn("Claim decision rejected: lifecycle or regulatory check failed");
            return DecisionOutcome.REJECTED;
        }

        DecisionOutcome result = decisionEngine.calculateDecision(tenantId, policyId, lossDate, idempotencyKey);
        auditLogger.info("Claim decision calculated successfully: idempotencyKey={}, outcome={}", idempotencyKey, result);
        return result;
    }
}
