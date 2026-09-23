package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurposeAggregateAndPresentFullDecisionContextInputsOutputsAndAuditEvidenceForFnolEventsTest {

    @Mock
    private FnolEventRepository fnolEventRepository;

    @Mock
    private DecisionContextBuilder decisionContextBuilder;

    @Mock
    private AuditEvidenceLogger auditEvidenceLogger;

    @InjectMocks
    private InsuredEngagementDecisionTransformer transformer;

    private static final String FNOL_ID = "fnol-123";
    private static final String CLAIM_ID = "claim-456";
    private static final String EXPOSURE_ID = "exp-789";

    @BeforeEach
    void setUp() {
        FnolEvent mockEvent = new FnolEvent(FNOL_ID, CLAIM_ID, EXPOSURE_ID, Instant.now(), "AUTO_COLLISION");
        when(fnolEventRepository.findById(FNOL_ID)).thenReturn(Optional.of(mockEvent));

        DecisionContext mockContext = new DecisionContext(
                UUID.randomUUID().toString(),
                Map.of("fnolId", FNOL_ID, "claimId", CLAIM_ID, "exposureId", EXPOSURE_ID),
                List.of("initial_triage", "coverage_check"),
                Map.of("decisionOutcome", "APPROVED", "riskScore", 85)
        );
        when(decisionContextBuilder.build(any(FnolEvent.class))).thenReturn(mockContext);

        AuditEvidence mockEvidence = new AuditEvidence(
                UUID.randomUUID().toString(),
                FNOL_ID,
                Instant.now(),
                "System generated audit trail for FNOL event",
                Map.of("complianceCheck", "PASSED", "gdprConsent", "GRANTED")
        );
        when(auditEvidenceLogger.log(any(AuditEvidence.class))).thenReturn(mockEvidence);
    }

    @Test
    void purpose_aggregate_and_present_full_decision_context_inputs_outputs_and_audit_evidence_for_fnol_events() {
        // Act
        DecisionContext result = transformer.transformAndAggregate(FNOL_ID);

        // Assert - Inputs
        assertNotNull(result, "Aggregated decision context should not be null");
        assertEquals(FNOL_ID, result.inputs().get("fnolId"), "FNOL ID should be present in inputs");
        assertEquals(CLAIM_ID, result.inputs().get("claimId"), "Claim ID should be present in inputs");
        assertEquals(EXPOSURE_ID, result.inputs().get("exposureId"), "Exposure ID should be present in inputs");

        // Assert - Outputs & Decision Context
        assertTrue(result.outputs().contains("initial_triage"), "Decision outputs should contain triage step");
        assertTrue(result.outputs().contains("coverage_check"), "Decision outputs should contain coverage check step");
        assertEquals("APPROVED", result.outputs().get("decisionOutcome"), "Decision outcome should be present");
        assertEquals(85, result.outputs().get("riskScore"), "Risk score should be present in decision context");

        // Verify mock interactions & audit evidence logging
        verify(fnolEventRepository, times(1)).findById(FNOL_ID);
        verify(decisionContextBuilder, times(1)).build(any(FnolEvent.class));
        verify(auditEvidenceLogger, times(1)).log(any(AuditEvidence.class));
        verifyNoMoreInteractions(fnolEventRepository, decisionContextBuilder, auditEvidenceLogger);
    }

    // Minimal DTOs for test context
    private record FnolEvent(String id, String claimId, String exposureId, Instant timestamp, String eventCode) {}
    private record DecisionContext(String aggregationId, Map<String, String> inputs, List<String> outputs, Map<String, Object> decisionContext) {}
    private record AuditEvidence(String evidenceId, String fnolId, Instant timestamp, String description, Map<String, String> complianceFlags) {}

    // Interfaces for mocked external I/O & services
    interface FnolEventRepository {
        Optional<FnolEvent> findById(String id);
    }

    interface DecisionContextBuilder {
        DecisionContext build(FnolEvent event);
    }

    interface AuditEvidenceLogger {
        AuditEvidence log(AuditEvidence evidence);
    }

    // Service under test
    static class InsuredEngagementDecisionTransformer {
        private final FnolEventRepository fnolEventRepository;
        private final DecisionContextBuilder decisionContextBuilder;
        private final AuditEvidenceLogger auditEvidenceLogger;

        InsuredEngagementDecisionTransformer(FnolEventRepository fnolEventRepository,
                                             DecisionContextBuilder decisionContextBuilder,
                                             AuditEvidenceLogger auditEvidenceLogger) {
            this.fnolEventRepository = fnolEventRepository;
            this.decisionContextBuilder = decisionContextBuilder;
            this.auditEvidenceLogger = auditEvidenceLogger;
        }

        DecisionContext transformAndAggregate(String fnolId) {
            FnolEvent event = fnolEventRepository.findById(fnolId)
                    .orElseThrow(() -> new IllegalArgumentException("FNOL event not found: " + fnolId));

            DecisionContext context = decisionContextBuilder.build(event);
            
            // Log audit evidence as part of the transformation pipeline
            auditEvidenceLogger.log(new AuditEvidence(
                    UUID.randomUUID().toString(),
                    fnolId,
                    Instant.now(),
                    "Aggregated decision context for FNOL",
                    Map.of("status", "COMPLETED", "pipelineStage", "TRANSFORMATION")
            ));

            return context;
        }
    }
}
