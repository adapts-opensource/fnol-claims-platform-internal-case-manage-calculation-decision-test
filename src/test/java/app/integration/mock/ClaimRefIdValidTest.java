package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock test for Claim Data Standardization:transformation:orchestration.
 * Validates orchestration logic with mocked infrastructure contracts.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    private static final String VALID_CLAIM_REF_ID = "CLM-NEWCO-2024-001";
    private static final String TABLE_NAME = "Claim Data Store_table";
    private static final String BUCKET_NAME = "Document Management-bucket";

    @BeforeEach
    void setUp() {
        // Setup shared mock behaviors if required
    }

    /**
     * Test Case: ClaimRefIdValid
     * Description: Claim ref ID valid
     * Verifies that orchestration proceeds successfully when a valid Claim Reference ID is present.
     */
    @Test
    void claim_ref_id_valid() {
        // Arrange
        Map<String, Object> payload = new HashMap<>();
        payload.put("claim_ref_id", VALID_CLAIM_REF_ID);
        payload.put("policy_number", "POL-12345");
        payload.put("status", "INITIATED");
        payload.put("insured_name", "Jane Doe");

        ClaimDataStandardizationStateTransitionOrch entity = new ClaimDataStandardizationStateTransitionOrch();
        entity.setId(VALID_CLAIM_REF_ID);
        entity.setPayload(payload);

        // Mock infrastructure responses
        Map<String, Object> expectedItem = new HashMap<>();
        expectedItem.put("pk", VALID_CLAIM_REF_ID);
        expectedItem.put("payload", payload);
        
        when(claimDataStoreClient.putItem(eq(TABLE_NAME), any(Map.class)))
            .thenReturn(Map.of("item_payload", expectedItem));
        
        when(documentManagementClient.putObject(eq(BUCKET_NAME), eq("CLM-NEWCO-2024-001.json"), any(String.class)))
            .thenReturn("s3://Document Management-bucket/CLM-NEWCO-2024-001.json");

        // Act
        OrchestrationResult result = orchestrator.transformAndOrchestrate(entity);

        // Assert
        assertNotNull(result, "Orchestration result should not be null for valid input");
        assertEquals(OrchestrationResult.Status.SUCCESS, result.getStatus(), "Status should be SUCCESS");
        assertEquals(VALID_CLAIM_REF_ID, result.getClaimRefId(), "Claim Ref ID should match input");

        // Verify infra interactions
        verify(claimDataStoreClient).putItem(eq(TABLE_NAME), any(Map.class));
        verify(documentManagementClient).putObject(eq(BUCKET_NAME), eq("CLM-NEWCO-2024-001.json"), any(String.class));
    }
}
