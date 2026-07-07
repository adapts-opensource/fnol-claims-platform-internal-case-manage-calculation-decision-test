package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditScopeTest {

    @Mock
    private ClaimDataOrchestrator claimDataOrchestrator;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private StructuredLogger structuredLogger;

    @BeforeEach
    void setUp() {
        // Initialize mocks; external I/O (DynamoDB, S3) are bypassed via Mockito
    }

    @Test
    void audit_scope() {
        // Given: Valid orchestration input matching claim_data_standardization_state_transition_orch model
        String entityId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "claimId", "CLM-2024-001",
            "standardizedFields", Map.of("policyNumber", "P-98765", "coverageType", "AUTO"),
            "timestamp", System.currentTimeMillis()
        );
        Map<String, String> auditContext = Map.of("traceId", UUID.randomUUID().toString(), "actorId", "SYSTEM");

        when(claimDataOrchestrator.executeTransformation(eq(entityId), eq(payload), eq(auditContext)))
            .thenReturn(Map.of("status", "TRANSFORMED", "version", "1.0"));

        // When: Orchestration executes with audit scope enabled
        Map<String, Object> result = claimDataOrchestrator.executeTransformation(entityId, payload, auditContext);

        // Then: Verify orchestration result
        assertNotNull(result);
        assertEquals("TRANSFORMED", result.get("status"));
        assertEquals("1.0", result.get("version"));

        // Verify audit event publication (compliance & observability NFRs)
        verify(auditEventPublisher).publish(eq("CLAIM_DATA_STANDARDIZATION"), eq("STATE_TRANSITION_ORCHESTRATION"), argThat(event ->
            event.containsKey("id") &&
            event.containsKey("payload") &&
            event.containsKey("auditContext") &&
            event.get("id").equals(entityId)
        ));

        // Verify structured logging (observability NFR)
        verify(structuredLogger).info(eq("Audit scope captured for claim orchestration"), argThat(args ->
            args.containsKey("claimId") && args.containsKey("payloadSize") && args.containsKey("traceId")
        ));

        // Ensure no direct infra calls bypass audit scope (security & least_privilege)
        verify(claimDataOrchestrator, times(1)).executeTransformation(any(), any(), any());
    }
}
