package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditTrailCapturesOverrideTest {

    @Mock
    private AuditTrailRepository auditTrailRepository;

    private InsuredEngagementService engagementService;

    @BeforeEach
    void setUp() {
        engagementService = new InsuredEngagementService(auditTrailRepository);
    }

    @Test
    void audit_trail_captures_override() {
        // Given
        String decisionId = "DEC-789";
        String previousState = "UNDER_REVIEW";
        String newState = "OVERRIDDEN";
        String operatorId = "compliance_officer_1";
        String overrideReason = "System error detected in automated decision";
        Instant overrideAt = Instant.parse("2024-05-20T14:30:00Z");

        // When
        engagementService.applyStateTransitionOverride(decisionId, previousState, newState, operatorId, overrideReason, overrideAt);

        // Then
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditTrailRepository, times(1)).recordEvent(eventCaptor.capture());

        Map<String, Object> capturedEvent = eventCaptor.getValue();
        assertEquals(decisionId, capturedEvent.get("entity_id"));
        assertEquals("STATE_TRANSITION_OVERRIDE", capturedEvent.get("event_type"));
        assertEquals(previousState, capturedEvent.get("previous_state"));
        assertEquals(newState, capturedEvent.get("new_state"));
        assertEquals(operatorId, capturedEvent.get("performed_by"));
        assertEquals(overrideReason, capturedEvent.get("reason"));
        assertEquals(overrideAt.toString(), capturedEvent.get("timestamp"));
    }

    // Minimal repository interface representing external audit persistence
    interface AuditTrailRepository {
        void recordEvent(Map<String, Object> event);
    }

    // Service under test delegating to mocked external I/O
    static class InsuredEngagementService {
        private final AuditTrailRepository auditTrailRepository;

        InsuredEngagementService(AuditTrailRepository auditTrailRepository) {
            this.auditTrailRepository = auditTrailRepository;
        }

        void applyStateTransitionOverride(String decisionId, String previousState, String newState, String operatorId, String reason, Instant timestamp) {
            Map<String, Object> auditEvent = new LinkedHashMap<>();
            auditEvent.put("entity_id", decisionId);
            auditEvent.put("event_type", "STATE_TRANSITION_OVERRIDE");
            auditEvent.put("previous_state", previousState);
            auditEvent.put("new_state", newState);
            auditEvent.put("performed_by", operatorId);
            auditEvent.put("reason", reason);
            auditEvent.put("timestamp", timestamp.toString());
            auditTrailRepository.recordEvent(auditEvent);
        }
    }
}
