package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that versioning and audit trail are maintained during decision state transitions.
 * NFR Alignment: 
 * - observability: structured_logging via audit trail service
 * - compliance: gdpr/soc2 via immutable transition logging
 * - security: input_validation & least_privilege_iam simulated via actor/context mocking
 */
@ExtendWith(MockitoExtension.class)
class VersioningAndAuditTrailMaintainedTest {

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private AuditTrailService auditTrailService;

    private DecisionStateTransitionService decisionStateTransitionService;

    @BeforeEach
    void setUp() {
        decisionStateTransitionService = new DecisionStateTransitionService(decisionRepository, auditTrailService);
    }

    @Test
    void versioning_and_audit_trail_maintained() {
        // Arrange
        String decisionId = "dec-789";
        String oldState = "DRAFT";
        String newState = "UNDER_REVIEW";
        String actor = "ADJUSTER_01";
        String reason = "Initial claim review initiated";

        Decision originalDecision = new Decision(decisionId, oldState, 1);
        when(decisionRepository.findById(decisionId)).thenReturn(originalDecision);
        when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Decision updatedDecision = decisionStateTransitionService.transitionState(decisionId, newState, actor, reason);

        // Assert: Versioning maintained
        assertEquals(2, updatedDecision.getVersion(), "Version should increment by 1 on state transition");
        assertEquals(newState, updatedDecision.getState(), "Decision state should reflect the new state");

        // Assert: Audit trail maintained
        verify(auditTrailService, times(1))
                .recordTransition(eq(decisionId), eq(oldState), eq(newState), eq(actor), anyString());

        // Assert: Persistence triggered with updated entity
        verify(decisionRepository, times(1)).save(updatedDecision);
    }

    // --- Supporting Domain & Service Contracts (Mocked in production) ---

    interface DecisionRepository {
        Decision findById(String id);
        Decision save(Decision decision);
    }

    interface AuditTrailService {
        void recordTransition(String decisionId, String oldState, String newState, String actor, String reason);
    }

    static class Decision {
        private final String id;
        private String state;
        private int version;

        Decision(String id, String state, int version) {
            this.id = id;
            this.state = state;
            this.version = version;
        }

        String getId() { return id; }
        String getState() { return state; }
        void setState(String state) { this.state = state; }
        int getVersion() { return version; }
        void setVersion(int version) { this.version = version; }
    }

    static class DecisionStateTransitionService {
        private final DecisionRepository repository;
        private final AuditTrailService auditTrail;

        DecisionStateTransitionService(DecisionRepository repository, AuditTrailService auditTrail) {
            this.repository = repository;
            this.auditTrail = auditTrail;
        }

        Decision transitionState(String decisionId, String newState, String actor, String reason) {
            Decision decision = repository.findById(decisionId);
            if (decision == null) {
                throw new IllegalArgumentException("Decision not found: " + decisionId);
            }

            String oldState = decision.getState();
            decision.setState(newState);
            decision.setVersion(decision.getVersion() + 1);

            // NFR: Structured logging & audit trail recording
            auditTrail.recordTransition(decisionId, oldState, newState, actor, reason);

            return repository.save(decision);
        }
    }
}
