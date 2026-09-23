package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Validates Claim Initiation & Routing orchestration transformation.
 * NFR Alignment: GDPR/SOC2 audit trails, thread-safe stateless mocks, structured logging simulation, TLS/IAM isolation via mocks.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingOrchestrationTransformationTest {

    @Mock
    private AcknowledgmentDispatcher acknowledgmentDispatcher;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private DeadlineValidator deadlineValidator;

    @Mock
    private Clock clock;

    @InjectMocks
    private ClaimOrchestrationService claimOrchestrationService;

    private static final String CLAIM_ID = "CLM-2024-001";
    private static final Instant INITIATION_TIME = Instant.parse("2024-01-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        // Deterministic time ensures reproducible statutory deadline checks (supports concurrency & observability)
        when(clock.instant()).thenReturn(INITIATION_TIME);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void purpose_validate_that_acknowledgments_were_sent_within_statutory_deadlines_and_log_compliance_status() {
        // Arrange
        Map<String, Object> claimPayload = Map.of("claimId", CLAIM_ID, "initiatedAt", INITIATION_TIME.toString());
        when(deadlineValidator.isWithinStatutoryDeadline(anyMap())).thenReturn(true);
        when(acknowledgmentDispatcher.sendAcknowledgment(anyString())).thenReturn(true);

        // Act
        TransformationOutcome outcome = claimOrchestrationService.transformAndRoute(claimPayload);

        // Assert: Acknowledgment dispatched within statutory window
        assertTrue(outcome.isAcknowledgmentSent(), "Acknowledgment must be dispatched during transformation");
        verify(acknowledgmentDispatcher, times(1)).sendAcknowledgment(eq(CLAIM_ID));

        // Assert: Compliance status logged with structured metadata (GDPR/SOC2 alignment)
        verify(complianceAuditService, times(1))
                .logComplianceStatus(eq(CLAIM_ID), eq(ComplianceStatus.COMPLIANT), anyString());

        // Assert: No external I/O calls (mocks guarantee isolation from AWS/HTTP)
        verifyNoMoreInteractions(acknowledgmentDispatcher, complianceAuditService, deadlineValidator, clock);
    }
}
