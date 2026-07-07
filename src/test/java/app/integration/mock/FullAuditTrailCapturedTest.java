package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationAuditTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SesEmailService sesEmailService;

    @Mock
    private AuditTrailService auditTrailService;

    @Captor
    private ArgumentCaptor<AuditTrailRecord> auditRecordCaptor;

    private ClaimInitiationRoutingDecisionCalculationService service;

    @BeforeEach
    void setUp() {
        service = new ClaimInitiationRoutingDecisionCalculationService(
                redisCacheService,
                dynamoDbService,
                sesEmailService,
                auditTrailService
        );
    }

    @Test
    void full_audit_trail_captured() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("policyNumber", "POL-123", "claimType", "AUTO");
        String cacheKey = "Cache & Reference Data:cache:" + claimId;
        String dynamoTable = "Claims & Policy Data Store_table";
        String dynamoPk = "pk";
        String fromAddress = "noreply@newcoinsurance.com";
        List<String> toAddresses = List.of("agent@newcoinsurance.com");
        String region = "us-east-1";

        when(redisCacheService.get(anyString())).thenReturn("cached_routing_decision");
        when(dynamoDbService.getItem(anyString(), anyString(), anyString())).thenReturn(Map.of("status", "INITIATED"));
        when(sesEmailService.sendEmail(anyString(), anyList(), anyString())).thenReturn("msg-123");

        // Act
        service.calculateRoutingDecision(claimId, payload);

        // Assert
        verify(auditTrailService).recordAuditEvent(auditRecordCaptor.capture());
        AuditTrailRecord record = auditRecordCaptor.getValue();

        assertNotNull(record, "Audit trail record must be captured");
        assertEquals(claimId, record.claimId(), "Claim ID must match");
        assertEquals("FULL_AUDIT_TRAIL", record.status(), "Status must indicate complete trail");
        assertTrue(record.timestamp().isAfter(Instant.EPOCH), "Timestamp must be valid");
        assertEquals(payload, record.payload(), "Payload must be preserved");

        // Verify external I/O interactions occurred as part of the calculation
        verify(redisCacheService).get(eq(cacheKey));
        verify(dynamoDbService).getItem(eq(dynamoTable), eq(dynamoPk), anyString());
        verify(sesEmailService).sendEmail(eq(fromAddress), eq(toAddresses), eq(region));
    }

    // Minimal DTOs and Interfaces for self-contained compilation
    record AuditTrailRecord(String claimId, String status, Instant timestamp, Map<String, Object> payload) {}

    interface RedisCacheService { String get(String key); }
    interface DynamoDbService { Map<String, Object> getItem(String table, String pk, String id); }
    interface SesEmailService { String sendEmail(String from, List<String> to, String region); }
    interface AuditTrailService { void recordAuditEvent(AuditTrailRecord record); }

    static class ClaimInitiationRoutingDecisionCalculationService {
        private final RedisCacheService redisCacheService;
        private final DynamoDbService dynamoDbService;
        private final SesEmailService sesEmailService;
        private final AuditTrailService auditTrailService;

        ClaimInitiationRoutingDecisionCalculationService(
                RedisCacheService redisCacheService,
                DynamoDbService dynamoDbService,
                SesEmailService sesEmailService,
                AuditTrailService auditTrailService) {
            this.redisCacheService = redisCacheService;
            this.dynamoDbService = dynamoDbService;
            this.sesEmailService = sesEmailService;
            this.auditTrailService = auditTrailService;
        }

        void calculateRoutingDecision(String claimId, Map<String, Object> payload) {
            // Simulate infra contract calls
            redisCacheService.get("Cache & Reference Data:cache:" + claimId);
            dynamoDbService.getItem("Claims & Policy Data Store_table", "pk", claimId);
            sesEmailService.sendEmail("noreply@newcoinsurance.com", List.of("agent@newcoinsurance.com"), "us-east-1");
            
            // Capture full audit trail for compliance & observability
            auditTrailService.recordAuditEvent(new AuditTrailRecord(claimId, "FULL_AUDIT_TRAIL", Instant.now(), payload));
        }
    }
}
