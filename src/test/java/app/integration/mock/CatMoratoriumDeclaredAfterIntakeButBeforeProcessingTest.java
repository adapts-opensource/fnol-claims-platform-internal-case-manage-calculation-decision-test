package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration tests for Claim Initiation & Routing:decision:calculation.
 * Verifies behavior using mocked dependencies to simulate external I/O and infrastructure.
 * Ensures thread safety via isolated test instances and structured logging via logger verification.
 */
class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private MoratoriumService moratoriumService;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private Logger decisionLogger;

    @Mock
    private InputValidator inputValidator;

    @InjectMocks
    private ClaimDecisionCalculationService claimDecisionService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    /**
     * Test Case: CatMoratoriumDeclaredAfterIntakeButBeforeProcessing
     * Scenario: A claim is initiated (intake), then a CAT moratorium is declared.
     * The decision calculation occurs after the moratorium start time.
     * Expected: Decision status is MORATORIUM, claim is rejected/routed accordingly.
     */
    @Test
    @DisplayName("Should return MORATORIUM decision when CAT moratorium declared after intake but before processing")
    void catMoratoriumDeclaredAfterIntakeButBeforeProcessing() {
        // Arrange
        String claimId = "claim-moratorium-001";
        LocalDateTime intakeTime = LocalDateTime.of(2023, 10, 27, 10, 0, 0);
        LocalDateTime moratoriumStartTime = LocalDateTime.of(2023, 10, 27, 10, 1, 0);
        LocalDateTime calculationTime = LocalDateTime.of(2023, 10, 27, 10, 5, 0);

        Map<String, Object> payload = Map.of(
            "claimId", claimId,
            "eventDate", intakeTime.toString(),
            "policyType", "PROPERTY"
        );

        // Mock Input Validation (Security NFR: input_validation)
        when(inputValidator.validateClaimPayload(anyMap())).thenReturn(true);

        // Mock Repository to return claim data
        ClaimInitiationData claimData = new ClaimInitiationData(claimId, payload);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claimData));

        // Mock Moratorium Service: Active because calculationTime > moratoriumStartTime
        when(moratoriumService.isMoratoriumActive("CAT", intakeTime, calculationTime))
            .thenReturn(true);

        // Act
        RoutingDecision decision = claimDecisionService.calculateAndRouteDecision(claimId, calculationTime);

        // Assert
        assertNotNull(decision, "Decision should not be null");
        assertEquals(claimId, decision.getClaimId(), "Claim ID should match");
        assertEquals(RoutingDecisionStatus.MORATORIUM, decision.getStatus(), 
            "Status should be MORATORIUM due to CAT event");
        assertEquals("CAT", decision.getMoratoriumType(), "Moratorium type should be CAT");

        // Verify Logging (Observability NFR: structured_logging)
        verify(decisionLogger).log(Level.INFO, "CAT moratorium active for claim", 
            "claimId", claimId, "intakeTime", intakeTime, "calculationTime", calculationTime);

        // Verify Persistence (Infra I/O Contract)
        ArgumentCaptor<RoutingDecision> decisionCaptor = ArgumentCaptor.forClass(RoutingDecision.class);
        verify(claimRepository).saveDecision(decisionCaptor.capture());
        
        RoutingDecision savedDecision = decisionCaptor.getValue();
        assertEquals(RoutingDecisionStatus.MORATORIUM, savedDecision.getStatus());
        assertNotNull(savedDecision.getCalculatedAt(), "Decision should have timestamp");
    }

    /**
     * Helper class simulating the data entity claim_initiation___routing_decision_validation.
     */
    static class ClaimInitiationData {
        private final String id;
        private final Map<String, Object> payload;

        public ClaimInitiationData(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }

        public String getId() { return id; }
        public Map<String, Object> getPayload() { return payload; }
    }

    /**
     * Helper class simulating the decision output.
     */
    static class RoutingDecision {
        private final String claimId;
        private final RoutingDecisionStatus status;
        private final String moratoriumType;
        private final LocalDateTime calculatedAt;

        public RoutingDecision(String claimId, RoutingDecisionStatus status, String moratoriumType, LocalDateTime calculatedAt) {
            this.claimId = claimId;
            this.status = status;
            this.moratoriumType = moratoriumType;
            this.calculatedAt = calculatedAt;
        }

        public String getClaimId() { return claimId; }
        public RoutingDecisionStatus getStatus() { return status; }
        public String getMoratoriumType() { return moratoriumType; }
        public LocalDateTime getCalculatedAt() { return calculatedAt; }
    }
}
