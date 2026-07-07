package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurposeValidateFnolDecisionsAgainstRegulatoryRulesAnd {

    @Mock
    private DynamoDbPersistence dynamoDbPersistence;
    @Mock
    private CommunicationService communicationService;
    @Mock
    private AuditTrailService auditTrailService;
    @Mock
    private RegulatoryRuleEngine regulatoryRuleEngine;

    private FnolDecisionStateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        stateTransitionService = new FnolDecisionStateTransitionService(
                dynamoDbPersistence,
                communicationService,
                auditTrailService,
                regulatoryRuleEngine
        );
    }

    @Test
    void purpose_validate_fnol_decisions_against_regulatory_rules_and_audit_requirements() {
        // Given
        String claimId = "CLM-1001";
        String newState = "DECISION_APPROVED";
        DecisionContext context = new DecisionContext("APPROVED", "USD", 5000.0);

        // Mock regulatory validation (GDPR/SOC2 compliance check)
        when(regulatoryRuleEngine.validateAgainstRules(claimId, newState, context)).thenReturn(true);
        // Mock data persistence (DynamoDB)
        when(dynamoDbPersistence.updateClaimState(claimId, newState)).thenReturn(true);
        // Mock audit logging (Compliance Diary)
        when(auditTrailService.logComplianceEvent(anyString(), anyString())).thenReturn("AUDIT-999");
        // Mock communication (SES)
        when(communicationService.sendNotification(anyString(), anyString())).thenReturn("MSG-001");

        // When
        String result = stateTransitionService.transitionAndValidate(claimId, newState, context);

        // Then
        assertEquals("TRANSITION_SUCCESS", result);
        verify(regulatoryRuleEngine, times(1)).validateAgainstRules(claimId, newState, context);
        verify(dynamoDbPersistence, times(1)).updateClaimState(claimId, newState);
        verify(auditTrailService, times(1)).logComplianceEvent(claimId, newState);
        verify(communicationService, times(1)).sendNotification(claimId, newState);
        verifyNoMoreInteractions(regulatoryRuleEngine, dynamoDbPersistence, auditTrailService, communicationService);
    }

    // Supporting interfaces/classes for standalone mock verification
    interface DynamoDbPersistence { boolean updateClaimState(String claimId, String newState); }
    interface CommunicationService { String sendNotification(String claimId, String newState); }
    interface AuditTrailService { String logComplianceEvent(String claimId, String newState); }
    interface RegulatoryRuleEngine { boolean validateAgainstRules(String claimId, String newState, DecisionContext context); }
    record DecisionContext(String approvalStatus, String currency, double amount) {}
    
    class FnolDecisionStateTransitionService {
        private final DynamoDbPersistence dynamoDbPersistence;
        private final CommunicationService communicationService;
        private final AuditTrailService auditTrailService;
        private final RegulatoryRuleEngine regulatoryRuleEngine;

        FnolDecisionStateTransitionService(DynamoDbPersistence dynamoDbPersistence, CommunicationService communicationService, AuditTrailService auditTrailService, RegulatoryRuleEngine regulatoryRuleEngine) {
            this.dynamoDbPersistence = dynamoDbPersistence;
            this.communicationService = communicationService;
            this.auditTrailService = auditTrailService;
            this.regulatoryRuleEngine = regulatoryRuleEngine;
        }

        String transitionAndValidate(String claimId, String newState, DecisionContext context) {
            if (!regulatoryRuleEngine.validateAgainstRules(claimId, newState, context)) {
                throw new IllegalStateException("Regulatory validation failed");
            }
            dynamoDbPersistence.updateClaimState(claimId, newState);
            auditTrailService.logComplianceEvent(claimId, newState);
            communicationService.sendNotification(claimId, newState);
            return "TRANSITION_SUCCESS";
        }
    }
}
