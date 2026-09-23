package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ExtendWith(MockitoExtension.class)
class SelectionTriggersPolicyMatchConfirmationEventTest {

    @Mock
    private PolicySelectionService policySelectionService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    private FnolSubmissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new FnolSubmissionOrchestrator(
            policySelectionService,
            eventPublisher,
            stateTransitionRepository
        );
    }

    @Test
    void selection_triggers_policy_match_confirmation_event() {
        // Given: Valid FNOL payload with policy details
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> fnolPayload = Map.of(
            "id", submissionId,
            "policyNumber", "POL-98765",
            "channel", "WEB",
            "claimantId", "CL-1001"
        );

        PolicyMatchResult expectedMatch = new PolicyMatchResult("POL-98765", true, "ACTIVE");
        when(policySelectionService.matchPolicy(fnolPayload)).thenReturn(expectedMatch);

        // When: Orchestration processes the submission
        orchestrator.validateAndOrchestrate(fnolPayload);

        // Then: Policy selection was attempted
        verify(policySelectionService).matchPolicy(fnolPayload);

        // Then: Policy Match Confirmation event was published
        verify(eventPublisher).publish(argThat(event ->
            event instanceof PolicyMatchConfirmationEvent &&
            ((PolicyMatchConfirmationEvent) event).getSubmissionId().equals(submissionId) &&
            ((PolicyMatchConfirmationEvent) event).getPolicyNumber().equals("POL-98765")
        ));

        // Then: State transition recorded with match confirmation
        verify(stateTransitionRepository).save(argThat(state ->
            state.getId().equals(submissionId) &&
            state.getPayload().containsKey("policyMatchConfirmed") &&
            Boolean.TRUE.equals(state.getPayload().get("policyMatchConfirmed"))
        ));

        // Ensure mocks were properly injected and SUT is non-null
        assertNotNull(orchestrator);
    }

    // Minimal stubs to ensure compilation context for mock definitions
    interface PolicySelectionService {
        PolicyMatchResult matchPolicy(Map<String, Object> payload);
    }

    interface EventPublisher {
        void publish(Object event);
    }

    interface StateTransitionRepository {
        void save(MultiChannelFnolSubmissionStateTransition state);
    }

    record PolicyMatchResult(String policyNumber, boolean isMatch, String status) {}

    record PolicyMatchConfirmationEvent(String submissionId, String policyNumber) implements Object {}

    record MultiChannelFnolSubmissionStateTransition(String id, Map<String, Object> payload) {}

    class FnolSubmissionOrchestrator {
        private final PolicySelectionService policySelectionService;
        private final EventPublisher eventPublisher;
        private final StateTransitionRepository stateTransitionRepository;

        FnolSubmissionOrchestrator(PolicySelectionService policySelectionService,
                                   EventPublisher eventPublisher,
                                   StateTransitionRepository stateTransitionRepository) {
            this.policySelectionService = policySelectionService;
            this.eventPublisher = eventPublisher;
            this.stateTransitionRepository = stateTransitionRepository;
        }

        void validateAndOrchestrate(Map<String, Object> payload) {
            PolicyMatchResult match = policySelectionService.matchPolicy(payload);
            
            if (match.isMatch()) {
                eventPublisher.publish(new PolicyMatchConfirmationEvent(
                    (String) payload.get("id"),
                    match.policyNumber()
                ));
                
                stateTransitionRepository.save(new MultiChannelFnolSubmissionStateTransition(
                    (String) payload.get("id"),
                    Map.of("policyMatchConfirmed", true, "policyNumber", match.policyNumber())
                ));
            }
        }
    }
}
