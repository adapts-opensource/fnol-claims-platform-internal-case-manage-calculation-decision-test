package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantVsLandlordOwnershipDisputeTest {

    @Mock
    private SubmissionValidationService validationService;

    @Mock
    private DataStore dataStore;

    @Mock
    private CommunicationHandler communicationHandler;

    private MultiChannelFnolSubmissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new MultiChannelFnolSubmissionOrchestrator(
                validationService, dataStore, communicationHandler
        );
    }

    @Test
    void tenantVsLandlordOwnershipDispute() {
        // Arrange
        String submissionId = "fnol-sub-tenant-landlord-001";
        Map<String, Object> submissionPayload = Map.of(
                "disputeType", "tenant_vs_landlord_ownership_dispute",
                "claimantRole", "tenant",
                "propertyOwnerRole", "landlord",
                "ownershipStatus", "disputed",
                "channel", "web_portal",
                "requiresManualReview", true
        );

        // Mock validation outcome for the specific dispute scenario
        when(validationService.validate(anyMap())).thenReturn(
                ValidationResult.builder()
                        .valid(true)
                        .submissionId(submissionId)
                        .disputeFlag("tenant_vs_landlord_ownership_dispute")
                        .build()
        );

        // Act
        ValidationResult result = orchestrator.processSubmission(submissionId, submissionPayload);

        // Assert
        assertNotNull(result, "Validation result must not be null");
        assertTrue(result.isValid(), "Payload should pass initial orchestration validation");
        assertEquals(submissionId, result.getSubmissionId(), "Submission ID must be preserved");
        assertEquals("tenant_vs_landlord_ownership_dispute", result.getDisputeFlag(), "Dispute type must be captured");

        // Verify cross-cutting infra I/O contracts are mocked and invoked correctly
        verify(validationService).validate(submissionPayload);
        verify(dataStore).persistItem(
                eq("multi_channel_fnol_submission_state_transition_c"),
                eq(submissionId),
                argThat(payload -> "tenant_vs_landlord_ownership_dispute".equals(payload.get("disputeType")))
        );
        verify(communicationHandler).sendNotification(
                eq("system@newco.insurance"),
                eq(List.of("disputes@newco.insurance", "claims-handler@newco.insurance")),
                argThat(msg -> msg.contains("tenant_vs_landlord_ownership_dispute"))
        );
        verifyNoInteractions(communicationHandler, dataStore); // Reset and re-verify specific calls if needed, but above covers it.
    }

    // Minimal domain and interface stubs to ensure standalone compilation and mock fidelity
    static class ValidationResult {
        private final boolean valid;
        private final String submissionId;
        private final String disputeFlag;

        private ValidationResult(boolean valid, String submissionId, String disputeFlag) {
            this.valid = valid;
            this.submissionId = submissionId;
            this.disputeFlag = disputeFlag;
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {
            private boolean valid;
            private String submissionId;
            private String disputeFlag;

            Builder valid(boolean v) { valid = v; return this; }
            Builder submissionId(String id) { submissionId = id; return this; }
            Builder disputeFlag(String flag) { disputeFlag = flag; return this; }
            ValidationResult build() { return new ValidationResult(valid, submissionId, disputeFlag); }
        }

        boolean isValid() { return valid; }
        String getSubmissionId() { return submissionId; }
        String getDisputeFlag() { return disputeFlag; }
    }

    interface SubmissionValidationService {
        ValidationResult validate(Map<String, Object> payload);
    }

    interface DataStore {
        void persistItem(String tableName, String id, Map<String, Object> payload);
    }

    interface CommunicationHandler {
        void sendNotification(String fromAddress, List<String> toAddresses, String body);
    }

    static class MultiChannelFnolSubmissionOrchestrator {
        private final SubmissionValidationService validationService;
        private final DataStore dataStore;
        private final CommunicationHandler communicationHandler;

        MultiChannelFnolSubmissionOrchestrator(SubmissionValidationService vs, DataStore ds, CommunicationHandler ch) {
            this.validationService = vs;
            this.dataStore = ds;
            this.communicationHandler = ch;
        }

        ValidationResult processSubmission(String id, Map<String, Object> payload) {
            ValidationResult result = validationService.validate(payload);
            if (result != null) {
                dataStore.persistItem("multi_channel_fnol_submission_state_transition_c", id, payload);
                communicationHandler.sendNotification(
                        "system@newco.insurance",
                        List.of("disputes@newco.insurance", "claims-handler@newco.insurance"),
                        "Processed submission: " + result.getDisputeFlag()
                );
            }
            return result;
        }
    }
}
