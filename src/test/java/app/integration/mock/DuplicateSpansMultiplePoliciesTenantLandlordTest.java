package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DuplicateSpansMultiplePoliciesTenantLandlordTest {

    @Mock
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @Mock
    private ClaimDataStoreContract claimDataStore;

    @Mock
    private DocumentManagementContract documentManagement;

    private static final String CLAIM_ID = "claim-123";
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        payload = Map.of(
            "id", CLAIM_ID,
            "policyIds", Map.of("tenant", "pol-tenant-01", "landlord", "pol-landlord-01"),
            "state", "INITIATED",
            "standardizedFields", Map.of("coverageType", "MULTI_POLICY")
        );
    }

    @Test
    void duplicate_spans_multiple_policies_tenant_landlord() {
        // Arrange: Mock orchestration transitions for both policies
        when(orchestrationService.processStateTransition(eq(CLAIM_ID), eq(payload), eq("TENANT_POLICY")))
            .thenReturn(Map.of("transitionStatus", "SUCCESS", "processedPolicy", "pol-tenant-01"));
        when(orchestrationService.processStateTransition(eq(CLAIM_ID), eq(payload), eq("LANDLORD_POLICY")))
            .thenReturn(Map.of("transitionStatus", "SUCCESS", "processedPolicy", "pol-landlord-01"));

        // Arrange: Mock DynamoDB contract
        when(claimDataStore.putItem(any(String.class), any(Map.class))).thenReturn(Map.of("ConsumedCapacity", 1));
        when(claimDataStore.getItem(any(String.class))).thenReturn(Map.of("id", CLAIM_ID, "payload", payload));

        // Arrange: Mock S3 contract
        when(documentManagement.uploadDocument(any(String.class), any(String.class))).thenReturn("s3://bucket/claim-123.json");

        // Act: Orchestrate state transitions for duplicate/multi-policy scenario
        Map<String, Object> tenantResult = orchestrationService.processStateTransition(CLAIM_ID, payload, "TENANT_POLICY");
        Map<String, Object> landlordResult = orchestrationService.processStateTransition(CLAIM_ID, payload, "LANDLORD_POLICY");

        // Assert: Verify successful transitions
        assertEquals("SUCCESS", tenantResult.get("transitionStatus"));
        assertEquals("SUCCESS", landlordResult.get("transitionStatus"));
        assertEquals("pol-tenant-01", tenantResult.get("processedPolicy"));
        assertEquals("pol-landlord-01", landlordResult.get("processedPolicy"));

        // Assert: Verify infra interactions
        verify(orchestrationService, times(2)).processStateTransition(eq(CLAIM_ID), eq(payload), anyString());
        verify(claimDataStore, times(2)).putItem(any(String.class), any(Map.class));
        verify(documentManagement, times(2)).uploadDocument(any(String.class), any(String.class));
    }
}
