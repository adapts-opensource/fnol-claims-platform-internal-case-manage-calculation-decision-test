package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Verifies that an invalid address triggers geocoding retries and transitions
 * the claim state to manual address correction, while satisfying NFRs for
 * observability, security, and compliance through mocked infrastructure.
 */
@ExtendWith(MockitoExtension.class)
public class InvalidAddressTriggerGeocodingRetryAndManualAddressTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private AddressValidationService addressValidationService;

    @Mock
    private StructuredLogger logger;

    @InjectMocks
    private StateTransitionOrchestrationService orchestrationService;

    @Test
    void invalid_address_trigger_geocoding_retry_and_manual_address_correction() {
        // Arrange: Initialize claim data with invalid address
        String claimId = "claim-123";
        Map<String, Object> initialPayload = new HashMap<>();
        initialPayload.put("address", "123 Invalid St, Nowhere, USA");
        initialPayload.put("geocodingRetryCount", 0);
        initialPayload.put("addressStatus", "INVALID");
        initialPayload.put("state", "STANDARDIZING");

        when(claimDataStoreClient.getItem("Claim Data Store_table", "pk", claimId))
                .thenReturn(Map.of("id", claimId, "payload", initialPayload));

        // Mock address validation failure
        when(addressValidationService.validate(anyString())).thenReturn(false);
        // Mock geocoding failures to trigger retry logic
        when(geocodingService.geocode(anyString())).thenReturn(null);

        // Act: Execute orchestration
        Map<String, Object> resultPayload = orchestrationService.processClaimData(claimId);

        // Assert: Verify state transition and payload updates
        assertNotNull(resultPayload);
        assertEquals("MANUAL_ADDRESS_CORRECTION", resultPayload.get("state"));
        assertEquals(2, resultPayload.get("geocodingRetryCount"));
        assertEquals("PENDING_MANUAL_REVIEW", resultPayload.get("addressStatus"));

        // Assert: Verify infrastructure I/O interactions (mocked, no live calls)
        verify(claimDataStoreClient, times(1)).putItem("Claim Data Store_table", "pk", anyMap());
        verify(documentManagementClient, times(1)).putObject(
                "Document Management-bucket", 
                eq("Document Management/" + claimId + ".json"), 
                any());
        verify(geocodingService, times(2)).geocode(anyString());
        verify(addressValidationService, times(1)).validate(anyString());
        
        // Assert: Verify structured logging for observability NFR
        Map<String, Object> logContext = new HashMap<>();
        logContext.put("claimId", claimId);
        logContext.put("action", "MANUAL_ADDRESS_CORRECTION_TRIGGERED");
        verify(logger, times(1)).info("Address validation failed, triggering manual correction", logContext);
    }
}
