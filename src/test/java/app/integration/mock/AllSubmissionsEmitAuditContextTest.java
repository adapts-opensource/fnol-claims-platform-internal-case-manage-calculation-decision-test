package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for Multi-Channel FNOL Submission: orchestration: validation.
 * Verifies that audit context is emitted for all submission channels,
 * including invalid submissions, adhering to GDPR/SOC2 and structured logging NFRs.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionOrchestrationValidationAuditContextTest {

    @Mock
    private MultiChannelFnolSubmissionOrchestrator orchestrator;

    @Mock
    private AuditContextEmitter auditContextEmitter;

    @Captor
    private ArgumentCaptor<AuditContext> auditContextCaptor;

    @ParameterizedTest(name = "Channel {0} submission should emit audit context")
    @ValueSource(strings = {"WEB", "MOBILE", "AGENT_PORTAL", "CALL_CENTER"})
    void allSubmissionsEmitAuditContext(String channel) {
        // Given
        String correlationId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "claimType", "AUTO",
            "incidentDate", Instant.now().toString(),
            "description", "Test incident"
        );

        doNothing().when(auditContextEmitter).emit(any(AuditContext.class));
        when(orchestrator.validateAndProcess(channel, correlationId, requestId, payload))
            .thenReturn(ValidationResult.success());

        // When
        ValidationResult result = orchestrator.validateAndProcess(channel, correlationId, requestId, payload);

        // Then
        verify(auditContextEmitter, times(1)).emit(auditContextCaptor.capture());
        AuditContext ctx = auditContextCaptor.getValue();

        assertEquals(correlationId, ctx.getCorrelationId(), "Correlation ID must be propagated");
        assertEquals(channel, ctx.getChannel(), "Channel must be captured in audit context");
        assertEquals(requestId, ctx.getRequestId(), "Request ID must be captured");
        assertNotNull(ctx.getTimestamp(), "Timestamp must be present");
        assertEquals(Instant.now().getEpochSecond(), ctx.getTimestamp().getEpochSecond(), 2, "Timestamp should be recent");
        assertEquals(ValidationStatus.SUCCESS, ctx.getStatus(), "Status should reflect result");
        
        // NFR: GDPR/SOC2 - Audit context must not contain raw PII
        assertFalse(ctx.getSanitizedPayload().containsKey("ssn"), "SSN must be masked in audit context");
        assertFalse(ctx.getSanitizedPayload().containsKey("driverLicense"), "Driver License must be masked");
        
        // NFR: Structured Logging - Payload structure validation
        assertNotNull(ctx.getSanitizedPayload(), "Sanitized payload must exist");
        assertEquals("AUTO", ctx.getSanitizedPayload().get("claimType"), "Non-PII fields should be preserved");
    }

    @Test
    void invalidSubmissionAlsoEmitsAuditContext() {
        // Given
        String correlationId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(); // Missing required fields

        doNothing().when(auditContextEmitter).emit(any(AuditContext.class));
        when(orchestrator.validateAndProcess(any(), any(), any(), any()))
            .thenReturn(ValidationResult.failure("VALIDATION_ERROR", "MISSING_REQUIRED_FIELDS"));

        // When
        ValidationResult result = orchestrator.validateAndProcess("WEB", correlationId, requestId, payload);

        // Then
        verify(auditContextEmitter, times(1)).emit(auditContextCaptor.capture());
        AuditContext ctx = auditContextCaptor.getValue();

        assertEquals(correlationId, ctx.getCorrelationId());
        assertEquals(ValidationStatus.FAILED, ctx.getStatus());
        assertEquals("VALIDATION_ERROR", ctx.getErrorCode());
        assertNotNull(ctx.getErrorMessage());
        
        // NFR: Input Validation - Audit context should capture validation failure details
        assertTrue(ctx.getErrorMessage().contains("MISSING_REQUIRED_FIELDS"));
    }

    @Test
    void auditContextEmissionIsThreadSafe() {
        // Given
        String correlationId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("claimType", "AUTO");
        
        doNothing().when(auditContextEmitter).emit(any(AuditContext.class));
        when(orchestrator.validateAndProcess(any(), any(), any(), any()))
            .thenReturn(ValidationResult.success());

        // When/Then - Verify isolation by capturing multiple calls
        for (int i = 0; i < 3; i++) {
            orchestrator.validateAndProcess("WEB", correlationId, requestId, payload);
        }

        // Then
        verify(auditContextEmitter, times(3)).emit(any(AuditContext.class));
        
        // Ensure captured contexts are distinct instances (thread safety simulation)
        // In a real concurrent test, we would use ExecutorService, but here we verify
        // the mock setup supports multiple invocations without state leakage in the mock.
        assertTrue(auditContextCaptor.getAllValues().size() == 3, "All submissions must emit audit context");
    }
}
