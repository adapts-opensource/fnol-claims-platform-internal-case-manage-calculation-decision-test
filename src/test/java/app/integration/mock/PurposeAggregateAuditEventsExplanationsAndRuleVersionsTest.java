package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurposeAggregateAuditEventsExplanationsAndRuleVersionsTest {

    @Mock
    private ClaimDataStandardizationService claimDataStandardizationService;

    private String claimId;
    private Map<String, Object> enrichedPayload;

    @BeforeEach
    void setUp() {
        claimId = "claim-std-validation-001";
        enrichedPayload = Map.of(
            "auditEvents", List.of("EVENT_INITIATED", "EVENT_VALIDATED"),
            "explanations", List.of("Standardization rule applied", "Data type coercion successful"),
            "ruleVersions", List.of("v1.2.0", "v2.0.0")
        );
    }

    @Test
    void purpose_aggregate_audit_events_explanations_and_rule_versions_for_a_claim_ensure_completeness_and_integrity() {
        // Arrange: Mock external I/O layer to return enriched claim payload
        when(claimDataStandardizationService.fetchEnrichedPayload(claimId))
            .thenReturn(enrichedPayload);

        // Act: Execute aggregation logic
        Map<String, Object> result = claimDataStandardizationService.aggregateAuditEventsExplanationsAndRuleVersions(claimId, enrichedPayload);

        // Assert: Completeness - verify all required aggregation keys exist
        assertNotNull(result, "Aggregated result must not be null");
        assertTrue(result.containsKey("claimId"), "Result must contain claimId");
        assertTrue(result.containsKey("auditEvents"), "Result must contain auditEvents");
        assertTrue(result.containsKey("explanations"), "Result must contain explanations");
        assertTrue(result.containsKey("ruleVersions"), "Result must contain ruleVersions");

        // Assert: Integrity - verify data consistency and expected counts
        assertEquals(claimId, result.get("claimId"), "Claim ID integrity check failed");
        assertEquals(2, ((List<?>) result.get("auditEvents")).size(), "Audit events count mismatch");
        assertEquals(2, ((List<?>) result.get("explanations")).size(), "Explanations count mismatch");
        assertEquals(2, ((List<?>) result.get("ruleVersions")).size(), "Rule versions count mismatch");

        // Verify mock interaction occurred exactly once
        verify(claimDataStandardizationService, times(1)).fetchEnrichedPayload(claimId);
    }
}
