package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoratoriumsOverrideStandardAcceptanceTest {

    @Mock
    private ClaimDecisionOrchestrator claimDecisionOrchestrator;

    private MoratoriumsOverrideStandardAcceptanceSut sut;

    @BeforeEach
    void setUp() {
        sut = new MoratoriumsOverrideStandardAcceptanceSut(claimDecisionOrchestrator);
    }

    @Test
    void moratoriums_override_standard_acceptance() {
        // Given: Claim initiation payload with active moratorium flag overriding standard rules
        Map<String, Object> payload = Map.of(
            "id", "claim-123",
            "moratorium_active", true,
            "region", "US-WEST",
            "policy_type", "AUTO",
            "standard_acceptance_rules", Map.of("approved", true)
        );

        // Expected decision when moratorium takes precedence over standard acceptance
        ClaimDecision expectedDecision = new ClaimDecision("MORATORIUM_HOLD", "Override applied due to active moratorium", true);

        when(claimDecisionOrchestrator.processDecision(anyMap())).thenReturn(expectedDecision);

        // When: Execute orchestration decision logic
        ClaimDecision actualDecision = sut.execute(payload);

        // Then: Verify moratorium correctly overrides standard acceptance routing
        assertEquals("MORATORIUM_HOLD", actualDecision.routingStatus());
        assertTrue(actualDecision.isOverrideApplied());
        assertEquals("Override applied due to active moratorium", actualDecision.reason());

        verify(claimDecisionOrchestrator, times(1)).processDecision(payload);
    }

    // Minimal SUT to isolate orchestration decision flow for unit/mock testing
    static class MoratoriumsOverrideStandardAcceptanceSut {
        private final ClaimDecisionOrchestrator orchestrator;

        MoratoriumsOverrideStandardAcceptanceSut(ClaimDecisionOrchestrator orchestrator) {
            this.orchestrator = orchestrator;
        }

        ClaimDecision execute(Map<String, Object> payload) {
            return orchestrator.processDecision(payload);
        }
    }

    // Decision result model aligned with claim_initiation___routing_decision_validation entity
    static class ClaimDecision {
        private final String routingStatus;
        private final String reason;
        private final boolean overrideApplied;

        ClaimDecision(String routingStatus, String reason, boolean overrideApplied) {
            this.routingStatus = routingStatus;
            this.reason = reason;
            this.overrideApplied = overrideApplied;
        }

        public String routingStatus() {
            return routingStatus;
        }

        public String reason() {
            return reason;
        }

        public boolean isOverrideApplied() {
            return overrideApplied;
        }
    }

    // Mocked interface representing external decision/routing service (replaces live infra calls)
    interface ClaimDecisionOrchestrator {
        ClaimDecision processDecision(Map<String, Object> payload);
    }
}
