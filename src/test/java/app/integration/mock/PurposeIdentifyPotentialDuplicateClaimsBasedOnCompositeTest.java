package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StateTransitionDuplicateClaimIdentificationTest {

    @Mock
    private HistoricalClaimRepository historicalClaimRepository;

    @InjectMocks
    private ClaimStateTransitionEngine claimStateTransitionEngine;

    private Claim incomingClaim;
    private CompositeKey compositeKey;

    @BeforeEach
    void setUp() {
        incomingClaim = new Claim("CLM-NEW-001", "POL-12345", "2024-05-20", "Jane Smith", "OPEN");
        compositeKey = new CompositeKey("POL-12345", "2024-05-20", "Jane Smith");
    }

    @Test
    void purpose_identify_potential_duplicate_claims_based_on_composite_keys_and_historical_state() {
        // Arrange: Simulate historical claim with matching composite keys but closed state
        Claim historicalClaim = new Claim("CLM-HIST-001", "POL-12345", "2024-05-20", "Jane Smith", "CLOSED");
        when(historicalClaimRepository.findByCompositeKey(compositeKey))
                .thenReturn(Optional.of(historicalClaim));

        // Act: Attempt state transition to ASSIGNED
        TransitionResult result = claimStateTransitionEngine.processStateTransition(incomingClaim, "ASSIGNED");

        // Assert: Composite key match triggers duplicate identification regardless of state difference
        assertTrue(result.isPotentialDuplicate(), "Should flag potential duplicate when composite keys match");
        assertEquals("CLM-HIST-001", result.getMatchingHistoricalClaimId(), "Should reference the historical claim ID");
        assertEquals("BLOCKED", result.getCurrentState(), "Should block transition pending manual review");
        assertEquals("Composite key match detected with historical state: CLOSED", result.getReason(), "Should explain block reason");
        
        // Verify external I/O was invoked exactly once and no other dependencies were touched
        verify(historicalClaimRepository, times(1)).findByCompositeKey(compositeKey);
        verifyNoOtherInteractions(historicalClaimRepository);
    }

    // Supporting domain models and interfaces (static to keep single-file test layout)
    record Claim(String claimId, String policyNumber, String incidentDate, String claimantName, String currentState) {}
    record CompositeKey(String policyNumber, String incidentDate, String claimantName) {}
    record TransitionResult(boolean potentialDuplicate, String matchingHistoricalClaimId, String currentState, String reason) {}

    interface HistoricalClaimRepository {
        Optional<Claim> findByCompositeKey(CompositeKey key);
    }

    class ClaimStateTransitionEngine {
        private final HistoricalClaimRepository repository;

        public ClaimStateTransitionEngine(HistoricalClaimRepository repository) {
            this.repository = repository;
        }

        public TransitionResult processStateTransition(Claim claim, String requestedState) {
            // Input validation (NFR: security)
            if (claim == null || claim.policyNumber() == null || claim.incidentDate() == null || claim.claimantName() == null) {
                throw new IllegalArgumentException("Claim composite fields must not be null");
            }

            CompositeKey key = new CompositeKey(claim.policyNumber(), claim.incidentDate(), claim.claimantName());
            Optional<Claim> historicalOpt = repository.findByCompositeKey(key);

            if (historicalOpt.isPresent()) {
                Claim historical = historicalOpt.get();
                // NFR: observability - structured logging placeholder
                // logger.info("Duplicate detection triggered", "policy", claim.policyNumber(), "historicalState", historical.currentState());
                return new TransitionResult(
                        true,
                        historical.claimId(),
                        "BLOCKED",
                        "Composite key match detected with historical state: " + historical.currentState()
                );
            }

            return new TransitionResult(false, null, requestedState, "Transition approved");
        }
    }
}
