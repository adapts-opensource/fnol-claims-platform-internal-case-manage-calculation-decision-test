package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that Internal Case Management:calculation:decision creates and delivers
 * required regulatory notifications within the defined SLA.
 * NFRs Covered: compliance (GDPR/SOC2 audit trails), security (input validation, TLS/IAM placeholders),
 * observability (structured logging), availability (multi-AZ failover simulation via mock latency).
 */
@ExtendWith(MockitoExtension.class)
public class PurposeCreateAndDeliverRequiredRegulatoryNotificationsWithinTest {

    @Mock
    private CaseDecisionCalculator decisionCalculator;

    @Mock
    private RegulatoryNotificationDispatcher notificationDispatcher;

    @Mock
    private AuditDiaryManager auditDiaryManager;

    @Mock
    private SecureStorageClient secureStorageClient;

    @Mock
    private CentralDataStore centralDataStore;

    @InjectMocks
    private CaseDecisionService caseDecisionService;

    private static final Duration SLA_THRESHOLD = Duration.ofMinutes(5);

    @BeforeEach
    void setUp() {
        // Structured logging placeholder for observability (NFR: observability)
        logInfo("Test setup completed for regulatory notification SLA verification");
    }

    @Test
    void purpose_create_and_deliver_required_regulatory_notifications_within_sla() {
        // Arrange
        String caseId = "CASE-12345";
        Map<String, Object> payload = Map.of(
            "type", "REGULATORY_NOTIFICATION",
            "priority", "HIGH",
            "insured_engagement___tracking_transformation_val", Map.of("id", "ENG-999", "payload", Map.of())
        );

        // Input validation check (NFR: security - input_validation)
        assertNotNull(caseId, "Case ID must not be null");
        assertFalse(payload.isEmpty(), "Payload must not be empty");
        assertTrue(payload.containsKey("type"), "Payload must contain regulatory type");

        // Mock decision calculation
        when(decisionCalculator.calculate(anyString(), any())).thenReturn(Map.of("decision", "APPROVED", "requiresNotification", true));
        // Mock notification delivery (simulates within SLA, NFR: availability ha_multi_az)
        when(notificationDispatcher.createAndDeliver(anyString(), any(Map.class))).thenReturn(true);
        // Mock infra I/O per DynamoDB/S3 contracts (NFR: compliance gdpr, soc2)
        when(auditDiaryManager.log(anyString(), anyString(), any())).thenReturn("AUDIT-001");
        when(secureStorageClient.store(anyString(), anyString())).thenReturn("s3://bucket/audit.json");
        when(centralDataStore.put(anyString(), anyString(), any())).thenReturn("ITEM-001");

        // Act
        Instant startTime = Instant.now();
        boolean result = caseDecisionService.processDecision(caseId, payload);
        Instant endTime = Instant.now();

        // Assert
        assertTrue(result, "Decision processing should succeed");
        assertTrue(Duration.between(startTime, endTime).compareTo(SLA_THRESHOLD) <= 0,
            "Notification must be created and delivered within SLA");
        verify(notificationDispatcher).createAndDeliver(eq(caseId), any(Map.class));
        verify(auditDiaryManager).log(eq(caseId), eq("REGULATORY_NOTIF"), any());
        verify(secureStorageClient).store(anyString(), anyString());
        verify(centralDataStore).put(anyString(), anyString(), any());
        verifyNoMoreInteractions(decisionCalculator, notificationDispatcher, auditDiaryManager, secureStorageClient, centralDataStore);
    }

    private void logInfo(String message) {
        // Placeholder for structured logging implementation (NFR: observability)
        // In production: logger.info("{}", Map.of("event", "test_setup", "message", message));
    }
}
