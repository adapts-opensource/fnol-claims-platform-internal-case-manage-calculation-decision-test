package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

/**
 * Tests for Claim Initiation & Routing: orchestration: transformation.
 * Verifies behavior when FNOL intake is submitted with policy number, address, or insured identity.
 */
class ClaimTransformationOrchestrationTest {

    @Mock
    private PolicyClaimsDBService policyClaimsDBService;

    @Mock
    private DocumentStorageService documentStorageService;

    private ClaimTransformationOrchestrationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClaimTransformationOrchestrationService(policyClaimsDBService, documentStorageService);
    }

    @Test
    void applies_when_fnol_intake_is_submitted_with_policy_number_address_or_insured_identity() {
        // Arrange: Simulate FNOL intake with policy number, address, and insured identity
        Map<String, Object> fnolPayload = Map.of(
            "policyNumber", "POL-2024-001",
            "address", Map.of("street", "123 Insurance Way", "city", "Springfield"),
            "insuredIdentity", "ID-ENTITY-999",
            "lossDate", "2024-05-20",
            "description", "Minor collision"
        );

        // Mock external dependencies: DynamoDB and S3
        String generatedClaimId = "CLM-2024-001";
        when(policyClaimsDBService.createClaim(any(Map.class)))
            .thenReturn(Map.of("claimId", generatedClaimId, "status", "INITIATED"));
        
        when(documentStorageService.storeDocument(anyString(), anyString(), any(byte[].class)))
            .thenReturn("s3://DocumentStorage-bucket/CLM-2024-001.json");

        // Act: Invoke transformation and routing
        Map<String, Object> result = service.transformAndRoute(fnolPayload);

        // Assert: Verify result and interactions
        assertNotNull(result, "Transformation result should not be null");
        assertEquals(generatedClaimId, result.get("claimId"), "Claim ID should match generated ID");
        assertEquals("INITIATED", result.get("status"), "Claim status should be INITIATED");

        // Verify PolicyClaimsDB was called with transformed payload containing required identifiers
        verify(policyClaimsDBService).createClaim(argThat(payload ->
            payload.containsKey("policyNumber") && 
            payload.containsKey("address") && 
            payload.containsKey("insuredIdentity")
        ), "PolicyClaimsDB should be called with transformed payload containing identifiers");

        // Verify DocumentStorage was called for audit/compliance storage
        verify(documentStorageService).storeDocument(
            eq("DocumentStorage-bucket"),
            eq("DocumentStorage/CLM-2024-001.json"),
            any(byte[].class)
        );
    }
}
