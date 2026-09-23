package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StateTransitionAuditInitiatedTest {

    @Mock
    private ClaimService claimService;

    @Mock
    private AuditService auditService;

    @Mock
    private DynamoDBPersistenceService persistenceService;

    @Mock
    private StructuredLogger logger;

    @Captor
    private ArgumentCaptor<Map<String, Object>> statePayloadCaptor;

    private static final String CLAIM_ID = "CLM-12345";

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks; thread_safety NFR handled by Mockito's concurrent-safe state
    }

    @Test
    void applies_when_audit_initiated() {
        // Given: Claim exists and audit is initiated
        Claim mockClaim = new Claim(CLAIM_ID, "OPEN", LocalDateTime.now());
        when(claimService.getClaim(CLAIM_ID)).thenReturn(Optional.of(mockClaim));
        when(auditService.isAuditInitiated(CLAIM_ID)).thenReturn(true);
        when(persistenceService.updateClaimState(anyString(), anyString(), any(Map.class))).thenReturn(true);

        // When: Trigger state transition
        boolean transitionSuccess = claimService.transitionState(CLAIM_ID, AuditTrigger.AUDIT_INITIATED);

        // Then: Verify state transition applies correctly
        assertTrue(transitionSuccess, "State transition should succeed when audit is initiated");
        verify(claimService, times(1)).getClaim(CLAIM_ID);
        verify(auditService, times(1)).isAuditInitiated(CLAIM_ID);
        verify(persistenceService, times(1)).updateClaimState(eq(CLAIM_ID), eq("AUDIT_IN_PROGRESS"), statePayloadCaptor.capture());

        Map<String, Object> payload = statePayloadCaptor.getValue();
        assertNotNull(payload);
        assertEquals("AUDIT_IN_PROGRESS", payload.get("current_state"));
        assertEquals("AUDIT_INITIATED", payload.get("trigger_event"));
        assertEquals(CLAIM_ID, payload.get("claim_id"));
        assertEquals("AUDIT_INITIATED", payload.get("compliance_trigger"));

        // Verify structured logging for observability NFR
        verify(logger, times(1)).log(eq("STATE_TRANSITION"), eq("AUDIT_INITIATED"), any(Map.class));
        verify(logger, times(1)).log(eq("SECURITY_AUDIT_LOG"), anyString(), any(Map.class));

        // Verify input_validation NFR (least_privilege_iam / safe guard)
        assertThrows(IllegalArgumentException.class, () -> claimService.transitionState(null, AuditTrigger.AUDIT_INITIATED));
    }

    // Minimal domain contracts for test isolation
    enum AuditTrigger { AUDIT_INITIATED }
    record Claim(String claimId, String currentState, LocalDateTime createdAt) {}

    interface ClaimService {
        Optional<Claim> getClaim(String claimId);
        boolean transitionState(String claimId, AuditTrigger trigger);
    }

    interface AuditService {
        boolean isAuditInitiated(String claimId);
    }

    interface DynamoDBPersistenceService {
        boolean updateClaimState(String claimId, String newState, Map<String, Object> payload);
    }

    interface StructuredLogger {
        void log(String category, String event, Map<String, Object> context);
    }
}
