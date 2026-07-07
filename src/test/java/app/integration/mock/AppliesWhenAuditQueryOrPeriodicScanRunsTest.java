package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppliesWhenAuditQueryOrPeriodicScanRuns {
    @Mock
    private AuditTriggerService auditTriggerService;
    @Mock
    private ClaimTransformationOrchestrator transformationOrchestrator;
    @Mock
    private PolicyClaimsDbService policyClaimsDbService;
    @Mock
    private ComplianceAuditService complianceAuditService;
    @Mock
    private Logger structuredLogger;

    private String claimId;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID().toString();
    }

    @Test
    void applies_when_audit_query_or_periodic_scan_runs() {
        // Arrange: Simulate audit query or periodic scan trigger
        when(auditTriggerService.isActiveAuditQueryOrPeriodicScan()).thenReturn(true);

        // Mock claim retrieval from DynamoDB
        Map<String, Object> originalClaim = Map.of(
            "claimId", claimId,
            "status", "INITIATED",
            "routingRule", "STANDARD_TIER_1"
        );
        when(policyClaimsDbService.fetchClaimById(anyString())).thenReturn(originalClaim);

        // Mock transformation logic
        Map<String, Object> transformedClaim = Map.of(
            "claimId", claimId,
            "status", "TRANSFORMED",
            "routingDestination", "AUTOMATED_ROUTING_QUEUE",
            "auditTimestamp", System.currentTimeMillis(),
            "validationStatus", "PASSED"
        );
        when(transformationOrchestrator.applyTransformation(anyMap())).thenReturn(transformedClaim);

        // Act
        Map<String, Object> result = transformationOrchestrator.processAuditTriggeredClaim(claimId);

        // Assert
        assertNotNull(result, "Transformed claim result should not be null");
        assertEquals("TRANSFORMED", result.get("status"), "Claim status should be updated to TRANSFORMED");
        assertEquals("AUTOMATED_ROUTING_QUEUE", result.get("routingDestination"), "Routing destination should be set");
        assertEquals("PASSED", result.get("validationStatus"), "Input validation should pass");

        // Verify orchestration interactions and NFR compliance
        verify(auditTriggerService, times(1)).isActiveAuditQueryOrPeriodicScan();
        verify(policyClaimsDbService, times(1)).fetchClaimById(claimId);
        verify(transformationOrchestrator, times(1)).applyTransformation(originalClaim);
        verify(complianceAuditService, times(1)).logAuditEvent(anyString(), anyString(), anyString());
        verify(structuredLogger, times(1)).info(anyString(), anyString(), anyString());
    }
}
