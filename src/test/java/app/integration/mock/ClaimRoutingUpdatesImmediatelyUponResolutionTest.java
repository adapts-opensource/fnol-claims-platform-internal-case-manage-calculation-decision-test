package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Claim Data Standardization:enrichment:validation.
 * Verifies that claim routing updates immediately upon resolution,
 * ensuring synchronous processing, data persistence, and validation flow.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimRoutingUpdatesImmediatelyUponResolution {

    @Mock
    private ClaimDataStandardizationDecisionValidationService validationService;

    @Mock
    private PolicyClaimDataStoreClient dynamoDbClient;

    @Mock
    private DocumentMediaStoreClient s3Client;

    @Mock
    private RoutingService routingService;

    @Mock
    private StructuredLogger logger;

    @InjectMocks
    private ClaimResolutionProcessor claimResolutionProcessor;

    private String claimId;
    private Map<String, Object> validPayload;

    @BeforeEach
    void setUp() {
        claimId = "CLM-TEST-RES-001";
        validPayload = new HashMap<>();
        validPayload.put("id", claimId);
        validPayload.put("status", "RESOLVED");
        validPayload.put("enrichmentSource", "external_provider");
        validPayload.put("validationStatus", "PASSED");
        validPayload.put("piiData", Map.of("masked", true));
    }

    @Test
    void claim_routing_updates_immediately_upon_resolution() {
        // Arrange: Mock synchronous validation and enrichment
        when(validationService.enrichAndValidate(anyMap())).thenReturn(validPayload);
        
        // Arrange: Mock DynamoDB persistence for Policy & Claim Data Store
        when(dynamoDbClient.putItem(eq("Policy & Claim Data Store_table"), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        // Arrange: Mock S3 interaction (expected not to be called for routing update)
        when(s3Client.putObject(anyString(), anyString(), any())).thenReturn(null);

        // Act: Process resolution
        boolean routingUpdated = claimResolutionProcessor.processResolution(claimId, validPayload);

        // Assert: Routing updates immediately (synchronous verification)
        assertTrue(routingUpdated, "Claim routing should update immediately upon resolution");
        verify(routingService, times(1)).resolveAndRoute(eq(claimId), anyMap());

        // Assert: Validation and enrichment occurred
        verify(validationService, times(1)).enrichAndValidate(anyMap());

        // Assert: Data persisted to Policy & Claim Data Store with correct structure
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dynamoDbClient, times(1)).putItem(eq("Policy & Claim Data Store_table"), payloadCaptor.capture());
        
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals(claimId, capturedPayload.get("id"), "Payload ID must match claim ID");
        assertEquals("RESOLVED", capturedPayload.get("status"), "Status must be RESOLVED");
        assertNotNull(capturedPayload.get("enrichmentSource"), "Enrichment metadata must be preserved");
        assertTrue((Boolean) capturedPayload.getOrDefault("piiData", Map.of("masked", true)).get("masked"), 
                   "PII masking constraint must be enforced per GDPR/SOC2");

        // Assert: S3 not called for this specific routing flow
        verify(s3Client, never()).putObject(anyString(), anyString(), any());

        // Assert: Structured logging for observability
        verify(logger, times(1)).info(eq("Claim resolution processed"), eq("claimId"), eq(claimId));
    }

    @Test
    void claim_routing_updates_immediately_upon_resolution_with_invalid_payload_fails_validation() {
        // Arrange: Invalid payload triggers validation failure
        Map<String, Object> invalidPayload = new HashMap<>();
        invalidPayload.put("id", claimId);
        invalidPayload.put("status", "RESOLVED");
        invalidPayload.put("missingRequiredField", null);

        when(validationService.enrichAndValidate(anyMap())).thenThrow(new IllegalArgumentException("Validation failed: missing required fields"));

        // Act & Assert: Exception propagates, routing is not updated
        assertThrows(IllegalArgumentException.class, () -> claimResolutionProcessor.processResolution(claimId, invalidPayload));
        
        // Verify routing service was NOT called due to validation failure
        verify(routingService, never()).resolveAndRoute(anyString(), anyMap());
        
        // Verify data store was NOT updated
        verify(dynamoDbClient, never()).putItem(anyString(), anyMap());
    }
}
