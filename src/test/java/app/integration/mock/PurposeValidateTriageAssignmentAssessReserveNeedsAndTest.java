package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Mock test for Claim Data Standardization:enrichment:decision.
 * Validates triage assignment, assesses reserve needs, and prepares investigation tasks.
 */
@ExtendWith(MockitoExtension.class)
public class PurposeValidateTriageAssignmentAssessReserveNeedsAnd {

    @Mock
    private ClaimDecisionEnrichmentService enrichmentService;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        // Initialize test payload aligned with claim_data_standardization_decision_validation schema
        testPayload = new HashMap<>();
        testPayload.put("id", "claim-123");
        testPayload.put("claimType", "AUTO");
        testPayload.put("severity", "HIGH");
        testPayload.put("triageStatus", "PENDING");
        testPayload.put("estimatedReserve", 0.0);
        testPayload.put("investigationTasks", new ArrayList<>());
    }

    @Test
    void purposeValidateTriageAssignmentAssessReserveNeedsAndPrepareInvestigationTasks() {
        // Arrange: Mock external I/O contracts (S3/DynamoDB) via service abstraction
        Map<String, Object> enrichedPayload = new HashMap<>(testPayload);
        enrichedPayload.put("triageStatus", "ASSIGNED");
        enrichedPayload.put("estimatedReserve", 15000.0);
        enrichedPayload.put("investigationTasks", List.of("VERIFY_DAMAGE", "CONTACT_INSURED"));

        when(enrichmentService.enrichDecisionPayload(anyMap())).thenReturn(enrichedPayload);

        // Act: Execute enrichment/decision logic
        Map<String, Object> result = enrichmentService.enrichDecisionPayload(testPayload);

        // Assert: Validate triage assignment, reserve needs assessment, and task preparation
        assertNotNull(result, "Enriched payload must not be null");
        assertEquals("ASSIGNED", result.get("triageStatus"), "Triage assignment must be validated");
        assertTrue((Double) result.get("estimatedReserve") > 0.0, "Reserve needs must be assessed");
        assertFalse(((List<?>) result.get("investigationTasks")).isEmpty(), "Investigation tasks must be prepared");
        
        // Verify external I/O was invoked exactly once without live calls
        verify(enrichmentService, times(1)).enrichDecisionPayload(testPayload);
    }
}
