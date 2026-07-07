package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementTransformationTest {

    @Mock
    private FnolSubmissionGateway fnolSubmissionGateway;

    @Mock
    private DecisionTransformationEngine decisionTransformationEngine;

    @InjectMocks
    private InsuredEngagementTrackingService insuredEngagementTrackingService;

    private FnolSubmission submission;

    @BeforeEach
    void setUp() {
        // Reset state between tests to ensure isolation
        submission = null;
    }

    @Test
    void applies_when_fnol_form_submitted_from_any_channel() {
        // Arrange: Simulate FNOL form submission from an arbitrary channel
        String channel = "agent_portal";
        submission = new FnolSubmission("FNOL-2024-001", channel, "INSURED-9876");

        // Act: Trigger the tracking service with the submission
        insuredEngagementTrackingService.processFnolSubmission(submission);

        // Assert: Verify that external I/O and transformation logic are invoked exactly once
        verify(fnolSubmissionGateway, times(1)).submit(submission);
        verify(decisionTransformationEngine, times(1)).applyTransformation(submission);
        assertEquals(channel, submission.getChannel(), "Channel metadata must be preserved through transformation");
    }

    // Minimal domain & service stubs for test compilation and isolation
    public static class FnolSubmission {
        private final String fnolId;
        private final String channel;
        private final String insuredId;

        public FnolSubmission(String fnolId, String channel, String insuredId) {
            this.fnolId = fnolId;
            this.channel = channel;
            this.insuredId = insuredId;
        }

        public String getChannel() { return channel; }
    }

    interface FnolSubmissionGateway {
        void submit(FnolSubmission submission);
    }

    interface DecisionTransformationEngine {
        void applyTransformation(FnolSubmission submission);
    }

    public static class InsuredEngagementTrackingService {
        private final FnolSubmissionGateway fnolSubmissionGateway;
        private final DecisionTransformationEngine decisionTransformationEngine;

        public InsuredEngagementTrackingService(FnlSubmissionGateway fnolSubmissionGateway, DecisionTransformationEngine decisionTransformationEngine) {
            this.fnolSubmissionGateway = fnolSubmissionGateway;
            this.decisionTransformationEngine = decisionTransformationEngine;
        }

        public void processFnolSubmission(FnolSubmission submission) {
            // Route to persistence/communication layer (mocked)
            fnolSubmissionGateway.submit(submission);
            // Apply decision & transformation logic (mocked)
            decisionTransformationEngine.applyTransformation(submission);
        }
    }
}
