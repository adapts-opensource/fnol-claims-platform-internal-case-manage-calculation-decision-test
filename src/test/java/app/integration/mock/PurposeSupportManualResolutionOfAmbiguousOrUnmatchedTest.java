package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static app.integration.mock.InsuredEngagementStateTransitionMockTest.EngagementState.MANUAL_RESOLUTION_REQUIRED;
import static app.integration.mock.InsuredEngagementStateTransitionMockTest.AmbiguityLevel.HIGH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Insured Engagement & Tracking: decision:state_transition.
 * Verifies state transition logic for ambiguous or unmatched policy contexts.
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementStateTransitionMockTest {

    @Mock
    private PolicyContextResolver policyContextResolver;

    @Mock
    private StateTransitionEngine stateTransitionEngine;

    @Mock
    private ComplianceDiaryService complianceDiaryService;

    @Mock
    private InputValidator inputValidator;

    @InjectMocks
    private InsuredEngagementCoordinator insuredEngagementCoordinator;

    @BeforeEach
    void setUp() {
        // Reset interactions between tests if this class were extended
    }

    /**
     * Test Case Label: PurposeSupportManualResolutionOfAmbiguousOrUnmatched
     * Test Name: purposeSupportManualResolutionOfAmbiguousOrUnmatchedPolicyContexts
     * Description: Purpose: Support manual resolution of ambiguous or unmatched policy contexts
     */
    @Test
    void purposeSupportManualResolutionOfAmbiguousOrUnmatchedPolicyContexts() {
        // Arrange
        String engagementId = "ENG-AMB-001";
        String insuredId = "INS-TEST-99";
        String rawContent = "Customer claims policy #9999 but system shows no match.";
        String channel = "CALL_CENTER";

        // Mock Input Validation (NFR: input_validation)
        ValidationResult validResult = new ValidationResult(true, List.of());
        when(inputValidator.validate(anyString())).thenReturn(validResult);

        // Mock Policy Context Analysis: Ambiguity detected (Unmatched/High Ambiguity)
        PolicyContextResolution resolution = new PolicyContextResolution()
                .setInsuredId(insuredId)
                .setAmbiguityLevel(HIGH)
                .setMatchScore(0.0)
                .setCandidates(Collections.emptyList())
                .setReason("NO_MATCHING_POLICY_FOUND");

        when(policyContextResolver.analyzeContext(eq(insuredId), eq(rawContent)))
                .thenReturn(resolution);

        EngagementPayload payload = new EngagementPayload(engagementId, insuredId, rawContent, channel);

        // Act
        insuredEngagementCoordinator.processEngagement(payload);

        // Assert State Transition
        ArgumentCaptor<StateTransitionRequest> transitionCaptor = ArgumentCaptor.forClass(StateTransitionRequest.class);
        verify(stateTransitionEngine).executeTransition(transitionCaptor.capture());

        StateTransitionRequest request = transitionCaptor.getValue();
        assertEquals(engagementId, request.getEngagementId());
        assertEquals(MANUAL_RESOLUTION_REQUIRED, request.getToState());
        assertEquals("AMBIGUOUS_POLICY_CONTEXT", request.getReason());
        assertNotNull(request.getTriggeredAt());

        // Assert Compliance Audit (NFR: soc2, observability)
        ArgumentCaptor<ComplianceEvent> eventCaptor = ArgumentCaptor.forClass(ComplianceEvent.class);
        verify(complianceDiaryService).logEvent(eventCaptor.capture());

        ComplianceEvent logEvent = eventCaptor.getValue();
        assertEquals(engagementId, logEvent.getEngagementId());
        assertEquals("STATE_TRANSITION_TRIGGERED", logEvent.getEventType());
        assertEquals(MANUAL_RESOLUTION_REQUIRED.name(), logEvent.getDetails().get("new_state"));
        assertEquals("AMBIGUOUS_POLICY_CONTEXT", logEvent.getDetails().get("reason"));

        // Assert No automatic binding occurred due to ambiguity
        verify(policyContextResolver, never()).bindPolicy(anyString(), anyString());
        verify(stateTransitionEngine, never()).executeTransition(any(StateTransitionRequest.class));
    }

    // --- Static Inner Classes for Mock Data Structures ---

    static class EngagementPayload {
        private String engagementId;
        private String insuredId;
        private String rawContent;
        private String channel;

        EngagementPayload(String engagementId, String insuredId, String rawContent, String channel) {
            this.engagementId = engagementId;
            this.insuredId = insuredId;
            this.rawContent = rawContent;
            this.channel = channel;
        }

        public String getEngagementId() { return engagementId; }
        public String getInsuredId() { return insuredId; }
        public String getRawContent() { return rawContent; }
        public String getChannel() { return channel; }
    }

    static class PolicyContextResolution {
        private String insuredId;
        private AmbiguityLevel ambiguityLevel;
        private double matchScore;
        private List<String> candidates;
        private String reason;

        PolicyContextResolution setInsuredId(String insuredId) { this.insuredId = insuredId; return this; }
        PolicyContextResolution setAmbiguityLevel(AmbiguityLevel ambiguityLevel) { this.ambiguityLevel = ambiguityLevel; return this; }
        PolicyContextResolution setMatchScore(double matchScore) { this.matchScore = matchScore; return this; }
        PolicyContextResolution setCandidates(List<String> candidates) { this.candidates = candidates; return this; }
        PolicyContextResolution setReason(String reason) { this.reason = reason; return this; }
    }

    static class StateTransitionRequest {
        private String engagementId;
        private EngagementState toState;
        private String reason;
        private LocalDateTime triggeredAt;

        public String getEngagementId() { return engagementId; }
        public void setEngagementId(String engagementId) { this.engagementId = engagementId; }
        public EngagementState getToState() { return toState; }
        public void setToState(EngagementState toState) { this.toState = toState; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public LocalDateTime getTriggeredAt() { return triggeredAt; }
        public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
    }

    static class ValidationResult {
        private boolean valid;
        private List<String> errors;

        ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }
    }

    static class ComplianceEvent {
        private String engagementId;
        private String eventType;
        private LocalDateTime timestamp;
        private java.util.Map<String, String> details;

        public String getEngagementId() { return engagementId; }
        public void setEngagementId(String engagementId) { this.engagementId = engagementId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public java.util.Map<String, String> getDetails() { return details; }
        public void setDetails(java.util.Map<String, String> details) { this.details = details; }
    }

    enum EngagementState {
        INITIATED,
        PROCESSING,
        MANUAL_RESOLUTION_REQUIRED,
        RESOLVED
    }

    enum AmbiguityLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    // --- Mock Service Interfaces (Simplified for Test Context) ---

    interface PolicyContextResolver {
        PolicyContextResolution analyzeContext(String insuredId, String content);
        void bindPolicy(String engagementId, String policyId);
    }

    interface StateTransitionEngine {
        void executeTransition(StateTransitionRequest request);
    }

    interface ComplianceDiaryService {
        void logEvent(ComplianceEvent event);
    }

    interface InputValidator {
        ValidationResult validate(String input);
    }

    interface InsuredEngagementCoordinator {
        void processEngagement(EngagementPayload payload);
    }
}
