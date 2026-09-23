package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ClaimRoutingDecisionCalculationAuditTest {

    @Mock
    private ClaimDecisionCalculationService calculationService;
    @Mock
    private AuditTrailRepository auditTrailRepo;
    @Mock
    private CacheService cacheService;
    @Mock
    private ClaimsDataStore claimsDataStore;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void audit_trail_captures_all_inputs_and_decisions() {
        // Given: Mocked inputs and expected decision
        String claimId = "CLM-INIT-78901";
        Map<String, Object> inputPayload = Map.of(
            "claimType", "AUTO",
            "damageEstimate", 4500.00,
            "policyId", "POL-55443",
            "timestamp", "2023-10-25T14:30:00Z"
        );
        Map<String, Object> calculatedDecision = Map.of(
            "routingPath", "REGIONAL_ADJUSTER",
            "priorityLevel", "STANDARD",
            "nextAction", "ASSIGN_TO_POOL"
        );

        // Stub external I/O (Cache & DynamoDB) to avoid live calls
        when(cacheService.get(eq("Cache & Reference Data:cache:policy:" + inputPayload.get("policyId"))))
                .thenReturn("VALID");
        when(claimsDataStore.fetchItem(eq("Claims & Policy Data Store_table"), any()))
                .thenReturn(inputPayload);
        when(calculationService.computeRoutingDecision(anyString(), anyMap()))
                .thenReturn(calculatedDecision);

        // When: Trigger calculation which should internally record to audit trail
        calculationService.computeRoutingDecision(claimId, inputPayload);

        // Then: Verify audit trail captured all inputs and decisions
        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> decisionCaptor = ArgumentCaptor.forClass(Map.class);

        verify(auditTrailRepo, times(1)).saveAuditRecord(
                eq(claimId),
                inputCaptor.capture(),
                decisionCaptor.capture(),
                anyString() // traceId
        );

        // Assert inputs were captured exactly as provided
        assertEquals(inputPayload, inputCaptor.getValue());
        // Assert decisions were captured exactly as calculated
        assertEquals(calculatedDecision, decisionCaptor.getValue());
        // Verify no other calls were made to audit trail for this execution
        verifyNoMoreInteractions(auditTrailRepo);
    }
}
