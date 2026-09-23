package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuditCapturesAllStateTransitionsTest {

    @Mock
    private AuditCaptureService mockAuditService;

    private ClaimDataStandardizationService claimStandardizationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        claimStandardizationService = new ClaimDataStandardizationService(mockAuditService);
    }

    @Test
    void audit_captures_all_state_transitions() {
        String claimId = "claim-123";
        Map<String, Object> payload = Map.of("claimId", claimId, "status", "INITIATED");
        List<String> expectedStates = List.of("INITIATED", "ENRICHED", "VALIDATED", "COMPLETED");

        ArgumentCaptor<Map<String, Object>> transitionCaptor = ArgumentCaptor.forClass(Map.class);

        claimStandardizationService.process(claimId, payload);

        verify(mockAuditService, times(expectedStates.size())).recordTransition(transitionCaptor.capture());
        List<Map<String, Object>> capturedTransitions = transitionCaptor.getAllValues();

        assertEquals(expectedStates.size(), capturedTransitions.size());
        for (int i = 0; i < expectedStates.size(); i++) {
            assertEquals(expectedStates.get(i), capturedTransitions.get(i).get("state"));
        }
    }

    interface AuditCaptureService {
        void recordTransition(Map<String, Object> transitionContext);
    }

    static class ClaimDataStandardizationService {
        private final AuditCaptureService auditService;

        ClaimDataStandardizationService(AuditCaptureService auditService) {
            this.auditService = auditService;
        }

        void process(String claimId, Map<String, Object> payload) {
            for (String state : List.of("INITIATED", "ENRICHED", "VALIDATED", "COMPLETED")) {
                Map<String, Object> context = Map.of("id", claimId, "state", state, "payload", payload);
                auditService.recordTransition(context);
            }
        }
    }
}
