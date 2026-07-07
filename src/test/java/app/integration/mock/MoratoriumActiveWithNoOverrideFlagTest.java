package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private MoratoriumChecker moratoriumChecker;
    @Mock
    private OverrideFlagValidator overrideFlagValidator;
    @Mock
    private DecisionAuditLogger auditLogger;

    private DecisionEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new DecisionEnrichmentService(moratoriumChecker, overrideFlagValidator, auditLogger);
    }

    @Test
    void moratorium_active_with_no_override_flag() {
        // Given
        String claimId = "CLM-MOR-001";
        when(moratoriumChecker.isActive(claimId)).thenReturn(true);
        when(overrideFlagValidator.hasOverride(claimId)).thenReturn(false);

        // When
        EnrichmentOutcome outcome = enrichmentService.processClaim(claimId);

        // Then
        assertEquals(DecisionStatus.MORATORIUM_BLOCKED, outcome.decisionStatus());
        assertFalse(outcome.overrideApplied());
        assertTrue(outcome.moratoriumActive());
        verify(moratoriumChecker).isActive(claimId);
        verify(overrideFlagValidator).hasOverride(claimId);
        verify(auditLogger).logDecision(eq(claimId), eq(DecisionStatus.MORATORIUM_BLOCKED));
        verifyNoMoreInteractions(moratoriumChecker, overrideFlagValidator, auditLogger);
    }

    // Supporting types for compilation completeness
    enum DecisionStatus { APPROVED, REJECTED, MORATORIUM_BLOCKED, PENDING }
    record EnrichmentOutcome(DecisionStatus decisionStatus, boolean overrideApplied, boolean moratoriumActive) {}
    interface MoratoriumChecker { boolean isActive(String claimId); }
    interface OverrideFlagValidator { boolean hasOverride(String claimId); }
    interface DecisionAuditLogger { void logDecision(String claimId, DecisionStatus status); }
    
    static class DecisionEnrichmentService {
        private final MoratoriumChecker moratoriumChecker;
        private final OverrideFlagValidator overrideFlagValidator;
        private final DecisionAuditLogger auditLogger;

        DecisionEnrichmentService(MoratoriumChecker m, OverrideFlagValidator v, DecisionAuditLogger l) {
            this.moratoriumChecker = m;
            this.overrideFlagValidator = v;
            this.auditLogger = l;
        }

        EnrichmentOutcome processClaim(String claimId) {
            boolean isMoratorium = moratoriumChecker.isActive(claimId);
            boolean hasOverride = overrideFlagValidator.hasOverride(claimId);
            if (isMoratorium && !hasOverride) {
                auditLogger.logDecision(claimId, DecisionStatus.MORATORIUM_BLOCKED);
                return new EnrichmentOutcome(DecisionStatus.MORATORIUM_BLOCKED, false, true);
            }
            return new EnrichmentOutcome(DecisionStatus.PENDING, false, false);
        }
    }
}
