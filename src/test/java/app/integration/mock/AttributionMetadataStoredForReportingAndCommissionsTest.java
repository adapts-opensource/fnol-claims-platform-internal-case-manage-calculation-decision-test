package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttributionMetadataStoredForReportingAndCommissionsTest {

    @Mock
    private ClaimDataStoreRepository claimDataStore;

    @Mock
    private ClaimOrchestrationService orchestrationService;

    private Map<String, Object> expectedPayload;

    @BeforeEach
    void setUp() {
        expectedPayload = new HashMap<>();
        expectedPayload.put("id", "CLM-12345");
        expectedPayload.put("payload", new HashMap<String, Object>() {{
            put("claimId", "CLM-12345");
            put("attribution", new HashMap<String, Object>() {{
                put("channel", "DIGITAL_PORTAL");
                put("campaignId", "CAMP-2024-Q1");
                put("agentId", "AGENT-9876");
                put("commissionTier", "PREMIUM");
            }});
            put("reportingMetadata", new HashMap<String, Object>() {{
                put("complianceFlags", "GDPR, SOC2");
                put("dataRetentionDays", 2555);
            }});
            put("standardizationVersion", "1.0");
        }});
    }

    @Test
    void attribution_metadata_stored_for_reporting_and_commissions() {
        // Arrange
        String claimId = "CLM-12345";
        Map<String, Object> inputPayload = new HashMap<>(expectedPayload);

        when(claimDataStore.save(anyString(), any(Map.class))).thenReturn(expectedPayload);

        // Act
        Map<String, Object> resultPayload = orchestrationService.processClaimData(claimId, inputPayload);

        // Assert
        assertNotNull(resultPayload, "Orchestrated payload must not be null");
        assertTrue(resultPayload.containsKey("payload"), "Payload container must exist");

        Map<String, Object> payloadMap = (Map<String, Object>) resultPayload.get("payload");
        assertNotNull(payloadMap, "Inner payload must exist");

        // Verify attribution metadata
        assertTrue(payloadMap.containsKey("attribution"), "Attribution metadata must be present for reporting");
        Map<String, Object> attribution = (Map<String, Object>) payloadMap.get("attribution");
        assertEquals("DIGITAL_PORTAL", attribution.get("channel"));
        assertEquals("CAMP-2024-Q1", attribution.get("campaignId"));
        assertEquals("AGENT-9876", attribution.get("agentId"));
        assertEquals("PREMIUM", attribution.get("commissionTier"));

        // Verify reporting/commissions metadata
        assertTrue(payloadMap.containsKey("reportingMetadata"), "Reporting metadata must be present for compliance");
        Map<String, Object> reporting = (Map<String, Object>) payloadMap.get("reportingMetadata");
        assertEquals("GDPR, SOC2", reporting.get("complianceFlags"));

        // Verify persistence contract
        verify(claimDataStore, times(1)).save(claimId, resultPayload);
    }

    // Minimal interfaces to satisfy mocking without external dependencies
    private interface ClaimDataStoreRepository {
        Map<String, Object> save(String partitionKey, Map<String, Object> itemPayload);
    }

    private interface ClaimOrchestrationService {
        Map<String, Object> processClaimData(String claimId, Map<String, Object> inputPayload);
    }
}
