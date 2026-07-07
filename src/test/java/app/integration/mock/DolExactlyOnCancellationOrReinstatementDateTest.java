package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class MultiChannelFnolValidationDecisionTest {

    private PolicyService policyService;
    private FnolDecisionService decisionService;

    @BeforeEach
    void setUp() {
        policyService = mock(PolicyService.class);
        decisionService = new FnolDecisionService(policyService);
    }

    @Test
    void dol_exactly_on_cancellation_or_reinstatement_date() {
        // Arrange
        LocalDate cancellationDate = LocalDate.of(2024, 5, 20);
        String policyId = "POL-" + UUID.randomUUID();
        LocalDate dateOfLoss = cancellationDate;

        when(policyService.getCancellationDate(policyId)).thenReturn(cancellationDate);
        when(policyService.getReinstatementDate(policyId)).thenReturn(null);

        FnolSubmission submission = new FnolSubmission();
        submission.setPolicyId(policyId);
        submission.setDateOfLoss(dateOfLoss);
        submission.setChannel("WEB");

        // Act
        DecisionResult result = decisionService.validateAndDecide(submission);

        // Assert
        assertNotNull(result);
        assertEquals(DecisionStatus.EDGE_CASE_DETECTED, result.getStatus());
        assertFalse(result.getMessages().isEmpty());
        assertTrue(result.getMessages().stream().anyMatch(msg -> msg.contains("cancellation date") || msg.contains("reinstatement date")));
        verify(policyService, times(1)).getCancellationDate(policyId);
    }

    // Supporting domain models and interfaces for test isolation
    static class FnolSubmission {
        private String policyId;
        private LocalDate dateOfLoss;
        private String channel;
        public void setPolicyId(String policyId) { this.policyId = policyId; }
        public String getPolicyId() { return policyId; }
        public void setDateOfLoss(LocalDate dateOfLoss) { this.dateOfLoss = dateOfLoss; }
        public LocalDate getDateOfLoss() { return dateOfLoss; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getChannel() { return channel; }
    }

    enum DecisionStatus {
        ACCEPTED, REJECTED, EDGE_CASE_DETECTED
    }

    static class DecisionResult {
        private final DecisionStatus status;
        private final List<String> messages;
        public DecisionResult(DecisionStatus status, List<String> messages) {
            this.status = status;
            this.messages = messages;
        }
        public DecisionStatus getStatus() { return status; }
        public List<String> getMessages() { return messages; }
    }

    interface PolicyService {
        LocalDate getCancellationDate(String policyId);
        LocalDate getReinstatementDate(String policyId);
    }

    static class FnolDecisionService {
        private final PolicyService policyService;
        public FnolDecisionService(PolicyService policyService) {
            this.policyService = policyService;
        }
        public DecisionResult validateAndDecide(FnolSubmission submission) {
            LocalDate cancellationDate = policyService.getCancellationDate(submission.getPolicyId());
            LocalDate reinstatementDate = policyService.getReinstatementDate(submission.getPolicyId());
            LocalDate dol = submission.getDateOfLoss();

            if (cancellationDate != null && dol != null && dol.isEqual(cancellationDate)) {
                return new DecisionResult(DecisionStatus.EDGE_CASE_DETECTED, List.of("DoL exactly on cancellation date"));
            }
            if (reinstatementDate != null && dol != null && dol.isEqual(reinstatementDate)) {
                return new DecisionResult(DecisionStatus.EDGE_CASE_DETECTED, List.of("DoL exactly on reinstatement date"));
            }
            return new DecisionResult(DecisionStatus.ACCEPTED, List.of("Validation passed"));
        }
    }
}
