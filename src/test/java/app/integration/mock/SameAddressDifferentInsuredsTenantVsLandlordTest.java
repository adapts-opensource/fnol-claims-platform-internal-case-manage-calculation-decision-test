package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SameAddressDifferentInsuredsTenantVsLandlordTest {

    @Mock
    private InsuredEngagementClient engagementClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void same_address_different_insureds_tenant_vs_landlord() {
        // Arrange: Define shared address and distinct insured payloads
        String sharedAddress = "100 Oak Avenue, Springfield, IL 62704";
        Map<String, Object> tenantPayload = Map.of(
            "insuredId", "INS-T-1001",
            "address", sharedAddress,
            "role", "TENANT",
            "engagementStatus", "PENDING"
        );
        Map<String, Object> landlordPayload = Map.of(
            "insuredId", "INS-L-1002",
            "address", sharedAddress,
            "role", "LANDLORD",
            "engagementStatus", "PENDING"
        );
        List<Map<String, Object>> batch = List.of(tenantPayload, landlordPayload);

        // Mock external I/O response (simulates DynamoDB/SES transformation outcome)
        Map<String, Object> mockResponse = Map.of(
            "transformationStatus", "SUCCESS",
            "processedRecords", 2,
            "decisions", List.of(
                Map.of("insuredId", "INS-T-1001", "decision", "APPROVED", "role", "TENANT"),
                Map.of("insuredId", "INS-L-1002", "decision", "APPROVED", "role", "LANDLORD")
            )
        );
        when(engagementClient.transformAndTrack(batch)).thenReturn(mockResponse);

        // Act
        Map<String, Object> result = engagementClient.transformAndTrack(batch);

        // Assert: Validate transformation outcome
        assertNotNull(result);
        assertEquals("SUCCESS", result.get("transformationStatus"));
        assertEquals(2, result.get("processedRecords"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) result.get("decisions");
        assertEquals(2, decisions.size());

        Map<String, Object> tenantDecision = decisions.stream()
            .filter(d -> "INS-T-1001".equals(d.get("insuredId")))
            .findFirst().orElseThrow();
        assertEquals("TENANT", tenantDecision.get("role"));
        assertEquals("APPROVED", tenantDecision.get("decision"));

        Map<String, Object> landlordDecision = decisions.stream()
            .filter(d -> "INS-L-1002".equals(d.get("insuredId")))
            .findFirst().orElseThrow();
        assertEquals("LANDLORD", landlordDecision.get("role"));
        assertEquals("APPROVED", landlordDecision.get("decision"));

        // Verify external I/O was invoked exactly once with correct payload
        ArgumentCaptor<List<Map<String, Object>>> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(engagementClient, times(1)).transformAndTrack(payloadCaptor.capture());
        assertEquals(2, payloadCaptor.getValue().size());
        assertEquals(sharedAddress, payloadCaptor.getValue().get(0).get("address"));
        assertEquals(sharedAddress, payloadCaptor.getValue().get(1).get("address"));
    }
}
