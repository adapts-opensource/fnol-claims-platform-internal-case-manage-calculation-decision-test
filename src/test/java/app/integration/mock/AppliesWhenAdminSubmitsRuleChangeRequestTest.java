package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingOrchestrationTest {

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private PolicyClaimsDbClient policyClaimsDbClient;

    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimOrchestrationService(
                complianceAuditService,
                documentStorageService,
                policyClaimsDbClient
        );
    }

    @Test
    void applies_when_admin_submits_rule_change_request() {
        // Arrange: Admin submits rule change request
        String adminId = "admin-001";
        String claimId = "CLM-7890";
        Map<String, Object> ruleChangePayload = new HashMap<>();
        ruleChangePayload.put("requestType", "RULE_CHANGE");
        ruleChangePayload.put("submittedBy", "admin");
        ruleChangePayload.put("claimId", claimId);
        ruleChangePayload.put("ruleDefinition", Map.of("threshold", 50000, "enabled", true));

        // Mock S3 audit logging (NFR: compliance, structured_logging, gdpr, soc2)
        when(complianceAuditService.writeAuditLog(anyString(), anyString()))
                .thenReturn("s3://ComplianceAuditService-bucket/ComplianceAuditService/" + claimId + ".json");

        // Mock DynamoDB state update (NFR: availability, concurrency, thread_safety)
        when(policyClaimsDbClient.updateClaimState(eq(claimId), anyMap()))
                .thenReturn(Map.of("status", "ROUTED_FOR_RULE_ENGINE", "version", "1.0"));

        // Act: Trigger orchestration & transformation
        Map<String, Object> result = orchestrationService.transformAndRouteClaim(adminId, ruleChangePayload);

        // Assert: Verify transformation applied, routing triggered, and audit logged
        assertNotNull(result, "Orchestration result must not be null");
        assertEquals("ROUTED_FOR_RULE_ENGINE", result.get("status"), "Claim must be routed after rule change transformation");
        assertTrue((Boolean) result.get("ruleChangeApplied"), "Rule change transformation flag must be true");

        // Verify external I/O mocks were invoked correctly
        verify(complianceAuditService, times(1)).writeAuditLog(eq("ComplianceAuditService-bucket"), contains(claimId));
        verify(policyClaimsDbClient, times(1)).updateClaimState(eq(claimId), argThat(map ->
                map.containsKey("status") && map.containsKey("version")
        ));
        verifyNoInteractions(documentStorageService);
    }
}
