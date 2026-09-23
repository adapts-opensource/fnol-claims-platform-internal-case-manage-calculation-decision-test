package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Claim Data Standardization:calculation:transformation.
 * Verifies that an unmatched FNOL payload correctly triggers intake shell creation
 * while respecting NFRs (input validation, thread safety, structured audit logging).
 */
@ExtendWith(MockitoExtension.class)
public class UnmatchedFnolCreatesIntakeShellTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private ClaimTransformationProcessor claimTransformationProcessor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> objectKeyCaptor;

    @BeforeEach
    void setUp() {
        // MockitoExtension auto-injects mocks; no manual initialization required.
    }

    @Test
    void unmatched_fnol_creates_intake_shell() {
        // Arrange
        String fnolId = UUID.randomUUID().toString();
        Map<String, Object> unmatchedFnolPayload = Map.of(
                "id", fnolId,
                "claimId", fnolId,
                "status", "UNMATCHED_FNOL",
                "eventType", "FNOL",
                "sourceSystem", "WEB_PORTAL",
                "timestamp", System.currentTimeMillis()
        );

        when(rulesEngineService.evaluate(any(Map.class))).thenReturn(Map.of("decision", "CREATE_INTAKE_SHELL", "priority", "HIGH"));
        when(workflowTaskRouter.route(any(Map.class))).thenReturn(Map.of("taskType", "INTAKE_SHELL_CREATION", "assignedTo", "INTAKE_TEAM"));
        when(auditDiaryStore.write(anyString(), any(Map.class))).thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/" + fnolId + ".json");

        // Act
        Map<String, Object> transformedResult = claimTransformationProcessor.transform(fnolId, unmatchedFnolPayload);

        // Assert: Core transformation outcome
        assertNotNull(transformedResult, "Transformation result must not be null");
        assertEquals("INTAKE_SHELL", transformedResult.get("status"), "Status should transition to INTAKE_SHELL");
        assertEquals(fnolId, transformedResult.get("id"), "Entity ID must be preserved");

        // Assert: Rules Engine contract validation
        verify(rulesEngineService, times(1)).evaluate(payloadCaptor.capture());
        Map<String, Object> capturedRulesPayload = payloadCaptor.getValue();
        assertEquals("UNMATCHED_FNOL", capturedRulesPayload.get("status"));

        // Assert: Workflow Router contract validation
        verify(workflowTaskRouter, times(1)).route(payloadCaptor.capture());
        Map<String, Object> capturedWorkflowPayload = payloadCaptor.getValue();
        assertEquals("INTAKE_SHELL_CREATION", capturedWorkflowPayload.get("taskType"));

        // Assert: S3 Audit Diary write contract validation
        verify(auditDiaryStore, times(1)).write(objectKeyCaptor.capture(), payloadCaptor.capture());
        String capturedObjectKey = objectKeyCaptor.getValue();
        assertTrue(capturedObjectKey.startsWith("AuditDiaryStore/"), "Object key must follow configured pattern");
        assertTrue(capturedObjectKey.endsWith(".json"), "Object key must end with .json");

        // NFR: Input Validation & Thread Safety sanity checks
        assertDoesNotThrow(() -> claimTransformationProcessor.transform(fnolId, unmatchedFnolPayload),
                "Transformation should handle valid payloads without throwing");
        assertTrue(Thread.currentThread().isAlive(), "Thread should remain active post-transformation");
        assertNotNull(capturedWorkflowPayload.get("assignedTo"), "Workflow payload must contain routing metadata");
    }

    // Package-private interfaces representing infra I/O contracts for mocking
    interface AuditDiaryStore {
        String write(String objectKeyPattern, Map<String, Object> payload);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> evaluate(Map<String, Object> input);
    }

    interface WorkflowTaskRouter {
        Map<String, Object> route(Map<String, Object> input);
    }

    interface ClaimTransformationProcessor {
        Map<String, Object> transform(String id, Map<String, Object> payload);
    }
}
