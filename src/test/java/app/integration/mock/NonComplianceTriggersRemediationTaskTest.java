package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NonComplianceTriggersRemediationTaskTest {

    @Mock
    private ComplianceAuditService complianceAuditService;
    @Mock
    private PolicyClaimsDB policyClaimsDB;
    @Mock
    private RemediationTaskService remediationTaskService;
    @Mock
    private ClaimTransformationOrchestrationService orchestrationService;

    private String testClaimId;

    @BeforeEach
    void setUp() {
        testClaimId = UUID.randomUUID().toString();
    }

    @Test
    void non_compliance_triggers_remediation_task() {
        // Arrange: Simulate non-compliant claim payload during transformation
        Map<String, Object> nonCompliantPayload = Map.of(
            "claimId", testClaimId,
            "type", "AUTO",
            "status", "INITIATED",
            "piiRedacted", false,
            "routingCode", "INVALID_ROUTE"
        );

        // Mock compliance validation to fail (triggers remediation path)
        when(orchestrationService.transformAndRoute(any())).thenThrow(new NonComplianceException("Validation failed: missing PII redaction or invalid routing"));

        // Mock S3 audit logging per ComplianceAuditService_s3 contract
        String expectedBucket = "ComplianceAuditService-bucket";
        String expectedKeyPattern = "ComplianceAuditService/" + testClaimId + ".json";
        String expectedUri = "s3://" + expectedBucket + "/" + expectedKeyPattern;
        when(complianceAuditService.logAuditEvent(eq(expectedBucket), eq(expectedKeyPattern), any())).thenReturn(expectedUri);

        // Mock DynamoDB update per PolicyClaimsDB_dynamodb contract
        String expectedTable = "PolicyClaimsDB_table";
        String expectedPk = "pk";
        Map<String, Object> updatedItem = Map.of("pk", testClaimId, "sk", "CLAIM", "status", "REMEDIATION_PENDING");
        when(policyClaimsDB.putItem(eq(expectedTable), eq(expectedPk), any())).thenReturn(updatedItem);

        // Act & Assert: Verify exception is thrown and remediation is triggered
        NonComplianceException thrownException = assertThrows(NonComplianceException.class, () ->
            orchestrationService.transformAndRoute(nonCompliantPayload)
        );

        assertEquals("Validation failed: missing PII redaction or invalid routing", thrownException.getMessage());

        // Verify remediation task creation
        ArgumentCaptor<Map<String, Object>> taskPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(remediationTaskService, times(1)).createRemediationTask(taskPayloadCaptor.capture());

        Map<String, Object> capturedTask = taskPayloadCaptor.getValue();
        assertEquals(testClaimId, capturedTask.get("claimId"));
        assertEquals("REMEDIATION_REQUIRED", capturedTask.get("taskStatus"));
        assertEquals("NON_COMPLIANCE", capturedTask.get("triggerReason"));

        // Verify audit trail and state persistence (Observability & Compliance NFRs)
        verify(complianceAuditService, times(1)).logAuditEvent(eq(expectedBucket), eq(expectedKeyPattern), any());
        verify(policyClaimsDB, times(1)).putItem(eq(expectedTable), eq(expectedPk), any());

        // Verify input validation and least privilege (Security NFRs)
        assertTrue(capturedTask.containsKey("sanitizedPayload"), "Remediation task must contain sanitized payload per least_privilege_iam");
    }
}

// Minimal interfaces to satisfy mock requirements without external dependencies
interface ComplianceAuditService {
    String logAuditEvent(String bucketName, String objectKeyPattern, Map<String, Object> payload);
}

interface PolicyClaimsDB {
    Map<String, Object> putItem(String tableName, String partitionKey, Map<String, Object> item);
}

interface RemediationTaskService {
    void createRemediationTask(Map<String, Object> taskPayload);
}

interface ClaimTransformationOrchestrationService {
    Map<String, Object> transformAndRoute(Map<String, Object> claimPayload);
}

class NonComplianceException extends RuntimeException {
    public NonComplianceException(String message) { super(message); }
}
