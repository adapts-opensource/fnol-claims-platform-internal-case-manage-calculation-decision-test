package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SameAddressDifferentInsuredTenantVsLandlordTest {

    @Mock
    private DynamoDBClient dynamoDBClient;

    @Mock
    private SesClient sesClient;

    @Mock
    private S3Client s3Client;

    private EngagementDecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new EngagementDecisionOrchestrator(dynamoDBClient, sesClient, s3Client);
    }

    @Test
    void same_address_different_insured_tenant_vs_landlord() {
        // Arrange: Simulate shared address with distinct insured roles (Tenant vs Landlord)
        String sharedAddress = "42 Elm Street";
        String tenantInsuredId = "INS-TENANT-99";
        String landlordInsuredId = "INS-LANDLORD-99";

        Map<String, Object> tenantRecord = Map.of(
            "exposure_id", "EXP-T-001",
            "insured_id", tenantInsuredId,
            "address", sharedAddress,
            "policy_type", "RENTAL",
            "role", "TENANT"
        );

        Map<String, Object> landlordRecord = Map.of(
            "exposure_id", "EXP-L-001",
            "insured_id", landlordInsuredId,
            "address", sharedAddress,
            "policy_type", "HO-3",
            "role", "LANDLORD"
        );

        when(dynamoDBClient.queryByAddress(sharedAddress))
            .thenReturn(List.of(tenantRecord, landlordRecord));

        // Act: Trigger decision orchestration for the shared address
        DecisionInput input = new DecisionInput(sharedAddress, Set.of(tenantInsuredId, landlordInsuredId));
        DecisionOutput output = orchestrator.evaluateEngagementDecision(input);

        // Assert: Verify correct segmentation and path assignment despite shared address
        assertNotNull(output);
        assertEquals(2, output.getSegmentedInsureds().size());
        assertTrue(output.getSegmentedInsureds().containsKey(tenantInsuredId));
        assertTrue(output.getSegmentedInsureds().containsKey(landlordInsuredId));

        // Verify tenant receives renters-specific engagement workflow
        assertEquals(EngagementPath.RENTERS_ONBOARDING, output.getWorkflowForInsured(tenantInsuredId));

        // Verify landlord receives property-owner-specific engagement workflow
        assertEquals(EngagementPath.PROPERTY_OWNER_VERIFICATION, output.getWorkflowForInsured(landlordInsuredId));

        // Verify external I/O remains mocked and untriggered (no cross-contamination alerts)
        verify(sesClient, never()).sendTransactionalEmail(any());
        verify(s3Client, never()).putObject(any(), any());
    }

    // Minimal domain contracts for compilation context
    record DecisionInput(String address, Set<String> insuredIds) {}
    record DecisionOutput(Map<String, String> segmentedInsureds, Map<String, EngagementPath> workflows) {
        public EngagementPath getWorkflowForInsured(String insuredId) {
            return workflows.get(insuredId);
        }
    }
    enum EngagementPath {
        RENTERS_ONBOARDING, PROPERTY_OWNER_VERIFICATION
    }

    // Mocked infrastructure clients
    interface DynamoDBClient { List<Map<String, Object>> queryByAddress(String address); }
    interface SesClient { void sendTransactionalEmail(String payload); }
    interface S3Client { void putObject(String bucket, String key); }

    // Service under test
    class EngagementDecisionOrchestrator {
        private final DynamoDBClient dynamoDBClient;
        private final SesClient sesClient;
        private final S3Client s3Client;

        EngagementDecisionOrchestrator(DynamoDBClient dynamoDBClient, SesClient sesClient, S3Client s3Client) {
            this.dynamoDBClient = dynamoDBClient;
            this.sesClient = sesClient;
            this.s3Client = s3Client;
        }

        DecisionOutput evaluateEngagementDecision(DecisionInput input) {
            List<Map<String, Object>> exposures = dynamoDBClient.queryByAddress(input.address());
            Map<String, String> segmented = new LinkedHashMap<>();
            Map<String, EngagementPath> workflows = new LinkedHashMap<>();

            for (Map<String, Object> exp : exposures) {
                String insuredId = (String) exp.get("insured_id");
                String role = (String) exp.get("role").toString().toUpperCase();
                segmented.put(insuredId, role);
                workflows.put(insuredId, "TENANT".equals(role) ? EngagementPath.RENTERS_ONBOARDING : EngagementPath.PROPERTY_OWNER_VERIFICATION);
            }
            return new DecisionOutput(segmented, workflows);
        }
    }
}
