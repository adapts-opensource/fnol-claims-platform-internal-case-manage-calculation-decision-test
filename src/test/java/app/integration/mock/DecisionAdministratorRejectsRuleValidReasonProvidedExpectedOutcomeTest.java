package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionStateTransitionIntegrationMockTest {

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private DecisionService decisionService;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private StructuredLogger logger;

    private DecisionContext decisionContext;

    @BeforeEach
    void setUp() {
        decisionContext = new DecisionContext();
        decisionContext.setId(UUID.randomUUID().toString());
        decisionContext.setInitialStatus(DecisionStatus.PENDING);
        decisionContext.setRuleId("rule_123");
    }

    @Test
    void decision_administrator_rejects_rule_valid_reason_provided_expected_outcome_status_updated_to_rejected_unmatched() {
        // Arrange
        String reason = "Valid reason provided for rejection";
        String adminId = "admin_user_01";
        DecisionStatus expectedStatus = DecisionStatus.REJECTED_UNMATCHED;

        when(decisionRepository.findById(decisionContext.getId())).thenReturn(Optional.of(decisionContext));
        when(decisionService.getDecisionContext(decisionContext.getId())).thenReturn(Optional.of(decisionContext));

        // Act
        decisionService.processDecisionTransition(
                decisionContext.getId(),
                adminId,
                DecisionAction.REJECT,
                reason
        );

        // Assert
        ArgumentCaptor<DecisionContext> contextCaptor = ArgumentCaptor.forClass(DecisionContext.class);
        verify(decisionRepository).save(contextCaptor.capture());
        DecisionContext savedContext = contextCaptor.getValue();

        assertEquals(expectedStatus, savedContext.getCurrentStatus(),
                "Status should be updated to Rejected_Unmatched after administrator rejection with valid reason.");
        assertEquals(reason, savedContext.getLastReason(),
                "Valid reason should be persisted.");
        assertEquals(adminId, savedContext.getLastUpdatedBy(),
                "Administrator ID should be recorded.");

        verify(complianceAuditService).logStateTransition(
                eq(decisionContext.getId()),
                eq(DecisionStatus.PENDING.name()),
                eq(expectedStatus.name()),
                anyString()
        );

        verify(logger).info(eq("Decision state transitioned"),
                eq("entityId"), eq(decisionContext.getId()),
                eq("fromStatus"), eq(DecisionStatus.PENDING.name()),
                eq("toStatus"), eq(expectedStatus.name()));
    }

    // Domain model stubs for isolated mocking
    enum DecisionStatus { PENDING, REJECTED_UNMATCHED }
    enum DecisionAction { REJECT }

    static class DecisionContext {
        private String id;
        private DecisionStatus initialStatus;
        private DecisionStatus currentStatus;
        private String ruleId;
        private String lastReason;
        private String lastUpdatedBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public DecisionStatus getInitialStatus() { return initialStatus; }
        public void setInitialStatus(DecisionStatus initialStatus) { this.initialStatus = initialStatus; }
        public DecisionStatus getCurrentStatus() { return currentStatus; }
        public void setCurrentStatus(DecisionStatus currentStatus) { this.currentStatus = currentStatus; }
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getLastReason() { return lastReason; }
        public void setLastReason(String lastReason) { this.lastReason = lastReason; }
        public String getLastUpdatedBy() { return lastUpdatedBy; }
        public void setLastUpdatedBy(String lastUpdatedBy) { this.lastUpdatedBy = lastUpdatedBy; }
    }

    interface DecisionRepository {
        Optional<DecisionContext> findById(String id);
        void save(DecisionContext context);
    }

    interface DecisionService {
        Optional<DecisionContext> getDecisionContext(String id);
        void processDecisionTransition(String id, String adminId, DecisionAction action, String reason);
    }

    interface ComplianceAuditService {
        void logStateTransition(String entityId, String fromStatus, String toStatus, String reason);
    }

    interface StructuredLogger {
        void info(String message, String key1, Object val1, String key2, Object val2, String key3, Object val3, String key4, Object val4);
    }
}
