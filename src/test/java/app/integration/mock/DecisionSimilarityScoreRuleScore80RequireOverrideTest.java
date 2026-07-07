package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Data Standardization:state_transition:orchestration.
 * Verifies rule enforcement for similarity score thresholds during state transitions.
 * Thread-safe via mock isolation; structured logging handled by underlying orchestrator.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchestrationMockTest {

    @Mock
    private ClaimDataStoreService claimDataStoreService;

    @Mock
    private DocumentManagementService documentManagementService;

    @Mock
    private RulesTriageService rulesTriageService;

    private StateTransitionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new StateTransitionOrchestrator(claimDataStoreService, documentManagementService, rulesTriageService);
    }

    @Test
    void decision_similarity_score_rule_score_80_require_override_notes_on_continue_expected_outcome_enforce_documentation() {
        // Arrange
        String claimId = "CLM-SIM-80-OVERRIDE";
        Map<String, Object> payload = new HashMap<>();
        payload.put("similarityScore", 85.0); // > 80% threshold
        payload.put("transitionAction", "continue");
        payload.put("overrideNotes", null); // Missing to trigger enforcement

        when(rulesTriageService.evaluateSimilarityRule(anyMap())).thenReturn(true);
        when(claimDataStoreService.fetchClaimData(anyString())).thenReturn(payload);

        // Act & Assert: Enforce documentation requirement
        assertThrows(ValidationException.class,
            () -> orchestrator.executeTransition(claimId, payload));

        // Verify rule evaluation was triggered
        verify(rulesTriageService).evaluateSimilarityRule(payload);
        // Verify state transition is blocked until documentation is enforced
        verify(claimDataStoreService, never()).persistClaimData(anyString(), anyMap());
        verify(documentManagementService, never()).uploadDocument(anyString(), anyMap());
    }
}

// Package-private stubs for compilation and mock isolation
interface ClaimDataStoreService {
    Map<String, Object> fetchClaimData(String id);
    void persistClaimData(String id, Map<String, Object> data);
}

interface DocumentManagementService {
    void uploadDocument(String bucket, Map<String, Object> metadata);
}

interface RulesTriageService {
    boolean evaluateSimilarityRule(Map<String, Object> payload);
}

class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}

class StateTransitionOrchestrator {
    private final ClaimDataStoreService claimDataStoreService;
    private final DocumentManagementService documentManagementService;
    private final RulesTriageService rulesTriageService;

    public StateTransitionOrchestrator(ClaimDataStoreService claimDataStoreService,
                                       DocumentManagementService documentManagementService,
                                       RulesTriageService rulesTriageService) {
        this.claimDataStoreService = claimDataStoreService;
        this.documentManagementService = documentManagementService;
        this.rulesTriageService = rulesTriageService;
    }

    public void executeTransition(String claimId, Map<String, Object> payload) {
        if (rulesTriageService.evaluateSimilarityRule(payload)) {
            Double score = (Double) payload.get("similarityScore");
            String action = (String) payload.get("transitionAction");
            String notes = (String) payload.get("overrideNotes");
            if (score != null && score > 80.0 && "continue".equalsIgnoreCase(action) && (notes == null || notes.isBlank())) {
                throw new ValidationException("Enforce documentation: Override notes required for similarity score > 80%");
            }
        }
    }
}
