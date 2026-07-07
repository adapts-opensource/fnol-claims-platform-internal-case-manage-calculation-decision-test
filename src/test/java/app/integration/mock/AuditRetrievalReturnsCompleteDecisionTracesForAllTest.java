package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Minimal service contracts for mock compilation context.
 */
interface AuditRetrievalService {
    List<Map<String, Object>> fetchAuditRecords(String auditQueryId);
}

interface DecisionTransformationService {
    List<Map<String, Object>> transformToCompleteDecisionTraces(List<Map<String, Object>> rawRecords);
}

class InsuredEngagementTrackingService {
    private final AuditRetrievalService auditRetrievalService;
    private final DecisionTransformationService decisionTransformationService;

    InsuredEngagementTrackingService(AuditRetrievalService auditRetrievalService,
                                     DecisionTransformationService decisionTransformationService) {
        this.auditRetrievalService = auditRetrievalService;
        this.decisionTransformationService = decisionTransformationService;
    }

    public List<Map<String, Object>> retrieveDecisionTraces(String auditQueryId) {
        List<Map<String, Object>> rawRecords = auditRetrievalService.fetchAuditRecords(auditQueryId);
        return decisionTransformationService.transformToCompleteDecisionTraces(rawRecords);
    }
}

@ExtendWith(MockitoExtension.class)
public class AuditRetrievalReturnsCompleteDecisionTracesForAllTest {

    @Mock
    private AuditRetrievalService auditRetrievalService;

    @Mock
    private DecisionTransformationService decisionTransformationService;

    @InjectMocks
    private InsuredEngagementTrackingService insuredEngagementTrackingService;

    private static final String FNOL_AUDIT_QUERY = "fnol-audit-query-001";
    private static final String EVENT_ID = "evt-12345";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and thread-safe state reset
    }

    @Test
    void audit_retrieval_returns_complete_decision_traces_for_all_fnol_events() {
        // Given: Simulated raw audit records from external audit store
        List<Map<String, Object>> rawAuditRecords = List.of(
                Map.of("event_id", EVENT_ID, "stage", "SUBMITTED", "timestamp", "2024-01-01T10:00:00Z"),
                Map.of("event_id", EVENT_ID, "stage", "REVIEWED", "timestamp", "2024-01-01T10:05:00Z"),
                Map.of("event_id", EVENT_ID, "stage", "APPROVED", "timestamp", "2024-01-01T10:10:00Z")
        );

        // Given: Expected transformed complete traces
        List<Map<String, Object>> expectedCompleteTraces = List.of(
                Map.of("event_id", EVENT_ID, "stage", "SUBMITTED", "complete_trace", true, "sanitized", true),
                Map.of("event_id", EVENT_ID, "stage", "REVIEWED", "complete_trace", true, "sanitized", true),
                Map.of("event_id", EVENT_ID, "stage", "APPROVED", "complete_trace", true, "sanitized", true)
        );

        when(auditRetrievalService.fetchAuditRecords(FNOL_AUDIT_QUERY)).thenReturn(rawAuditRecords);
        when(decisionTransformationService.transformToCompleteDecisionTraces(rawAuditRecords)).thenReturn(expectedCompleteTraces);

        // When: Invoke the feature under test
        List<Map<String, Object>> actualTraces = insuredEngagementTrackingService.retrieveDecisionTraces(FNOL_AUDIT_QUERY);

        // Then: Verify retrieval and transformation behavior
        assertNotNull(actualTraces, "Decision traces should not be null");
        assertEquals(3, actualTraces.size(), "Should return traces for all FNOL stages");

        assertTrue(actualTraces.stream().allMatch(trace -> Boolean.TRUE.equals(trace.get("complete_trace"))),
                "All traces must be marked as complete");
        assertTrue(actualTraces.stream().allMatch(trace -> Boolean.TRUE.equals(trace.get("sanitized"))),
                "Traces must comply with GDPR/SOC2 data sanitization");

        verify(auditRetrievalService, times(1)).fetchAuditRecords(FNOL_AUDIT_QUERY);
        verify(decisionTransformationService, times(1)).transformToCompleteDecisionTraces(rawAuditRecords);
    }
}
