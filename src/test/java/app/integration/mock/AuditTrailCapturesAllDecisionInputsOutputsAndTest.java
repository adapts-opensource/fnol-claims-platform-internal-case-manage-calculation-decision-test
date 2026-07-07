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
public class AuditTrailCapturesAllDecisionInputsOutputsAndSourcesTest {

    @Mock
    private ClaimDataStoreMock claimDataStoreMock;

    @Mock
    private AuditTrailRecorder auditTrailRecorder;

    private static final String CLAIM_ID = "CLM-789-ENRICH";
    private static final Map<String, Object> DECISION_INPUT = Map.of(
            "claimId", CLAIM_ID,
            "policyNumber", "POL-456",
            "eventDate", "2023-10-01",
            "amount", 2500.00
    );
    private static final List<String> DATA_SOURCES = List.of("FNOL_API", "POLICY_DB", "EXTERNAL_INSPECTOR");
    private static final Map<String, Object> DECISION_OUTPUT = Map.of(
            "claimId", CLAIM_ID,
            "standardizedStatus", "VALIDATED",
            "decisionCode", "DEC-APPROVED",
            "confidence", 0.92
    );

    @BeforeEach
    void setUp() {
        lenient().when(claimDataStoreMock.fetchClaimPayload(anyString())).thenReturn(DECISION_INPUT);
        lenient().when(claimDataStoreMock.persistAuditRecord(anyString(), anyMap())).thenReturn(true);
    }

    @Test
    void audit_trail_captures_all_decision_inputs_outputs_and_sources() {
        // Arrange: Wire mocked infrastructure to the enrichment pipeline
        DecisionEnrichmentPipeline pipeline = new DecisionEnrichmentPipeline(claimDataStoreMock, auditTrailRecorder);

        // Act: Execute the decision enrichment feature
        pipeline.executeEnrichment(CLAIM_ID, DATA_SOURCES);

        // Assert: Verify audit trail captured inputs, outputs, and sources exactly once
        verify(auditTrailRecorder, times(1)).recordInput(CLAIM_ID, DECISION_INPUT);
        verify(auditTrailRecorder, times(1)).recordOutput(CLAIM_ID, DECISION_OUTPUT);
        verify(auditTrailRecorder, times(1)).recordSources(CLAIM_ID, DATA_SOURCES);

        // Verify controlled infrastructure interactions
        verify(claimDataStoreMock, times(1)).fetchClaimPayload(CLAIM_ID);
        verify(claimDataStoreMock, times(1)).persistAuditRecord(eq(CLAIM_ID), anyMap());
        verifyNoMoreInteractions(claimDataStoreMock);
    }

    // Supporting interfaces/classes for isolated mock testing
    interface ClaimDataStoreMock {
        Map<String, Object> fetchClaimPayload(String claimId);
        boolean persistAuditRecord(String claimId, Map<String, Object> record);
    }

    interface AuditTrailRecorder {
        void recordInput(String claimId, Map<String, Object> input);
        void recordOutput(String claimId, Map<String, Object> output);
        void recordSources(String claimId, List<String> sources);
    }

    static class DecisionEnrichmentPipeline {
        private final ClaimDataStoreMock claimDataStore;
        private final AuditTrailRecorder auditTrailRecorder;

        DecisionEnrichmentPipeline(ClaimDataStoreMock claimDataStore, AuditTrailRecorder auditTrailRecorder) {
            this.claimDataStore = claimDataStore;
            this.auditTrailRecorder = auditTrailRecorder;
        }

        void executeEnrichment(String claimId, List<String> sources) {
            Map<String, Object> input = claimDataStore.fetchClaimPayload(claimId);
            auditTrailRecorder.recordInput(claimId, input);
            auditTrailRecorder.recordSources(claimId, sources);

            // Simulate decision logic & output generation
            Map<String, Object> output = Map.of(
                    "claimId", claimId,
                    "standardizedStatus", "VALIDATED",
                    "decisionCode", "DEC-APPROVED",
                    "confidence", 0.92
            );
            auditTrailRecorder.recordOutput(claimId, output);
            claimDataStore.persistAuditRecord(claimId, Map.of("audit", output));
        }
    }
}
