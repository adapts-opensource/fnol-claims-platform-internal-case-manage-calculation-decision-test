package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Initiation & Routing:decision:calculation.
 * Verifies routing decision logic when an audit request is submitted.
 * Mocks external I/O (Redis, DynamoDB, SES) to ensure no live AWS/HTTP calls.
 * Aligns with thread-safety, GDPR/SOC2 compliance, and structured observability NFRs.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SesEmailService sesEmailService;

    @InjectMocks
    private ClaimRoutingDecisionCalculator claimRoutingDecisionCalculator;

    @BeforeEach
    void setUp() {
        // Reset mocks and establish baseline state for each test execution
    }

    @Test
    void applies_when_audit_request_submitted() {
        // Arrange
        String claimId = "claim-audit-001";
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "requestType", "AUDIT",
                "submittedBy", "compliance_officer",
                "payload", Map.of("auditFlag", true)
        );

        when(redisCacheService.get("Cache & Reference Data:cache:" + claimId)).thenReturn("REFERENCE_DATA_ACTIVE");
        when(dynamoDbService.getItem("Claims & Policy Data Store_table", claimId)).thenReturn(Map.of("claimStatus", "OPEN"));

        // Act
        Map<String, Object> decision = claimRoutingDecisionCalculator.calculate(payload);

        // Assert
        assertNotNull(decision, "Routing decision must be generated");
        assertEquals("AUDIT_ROUTING_QUEUE", decision.get("routingTarget"));
        assertTrue((Boolean) decision.get("isAuditRequest"));
        assertEquals(claimId, decision.get("claimId"));

        // Verify I/O contracts: Audit submissions bypass standard SES notifications per security/compliance NFRs
        verifyNoInteractions(sesEmailService);
        verify(redisCacheService).get("Cache & Reference Data:cache:" + claimId);
        verify(dynamoDbService).getItem("Claims & Policy Data Store_table", claimId);
    }
}
