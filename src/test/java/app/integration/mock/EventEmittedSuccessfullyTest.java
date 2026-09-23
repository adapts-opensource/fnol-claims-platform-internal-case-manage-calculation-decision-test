package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationCalculationTransformEventTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private EventEmitter eventEmitter;

    private String claimId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID().toString();
        payload = Map.of(
            "claim_amount", 2500.00,
            "currency", "USD",
            "calculation_type", "REPAIR_COST",
            "status", "INITIATED"
        );
    }

    @Test
    void event_emitted_successfully() {
        // Arrange: Mock infrastructure I/O contracts
        when(auditDiaryStore.write(anyString(), anyString())).thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/" + claimId + ".json");
        when(rulesEngineDecisionService.query(anyString(), anyString())).thenReturn(Map.of("rule_id", "R-101", "decision", "APPROVE"));
        when(workflowTaskRouter.route(anyString(), anyString())).thenReturn("TASK_ASSIGNED");

        ClaimDataStandardizationCalculationTransformService service = new ClaimDataStandardizationCalculationTransformService(
            auditDiaryStore, rulesEngineDecisionService, workflowTaskRouter, eventEmitter
        );

        // Act: Execute transformation
        String result = service.transform(claimId, payload);

        // Assert: Verify transformation succeeded
        assertNotNull(result);
        assertTrue(result.contains("SUCCESS"));

        // Verify event emission
        verify(eventEmitter, times(1)).emit(eq("CLAIM_TRANSFORM_COMPLETE"), eq(claimId), any(Map.class));
    }
}
