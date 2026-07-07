package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock integration tests for Claim Data Standardization:transformation:orchestration.
 * Verifies orchestration logic with mocked infrastructure contracts (DynamoDB, S3).
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private RulesTriageServiceClient rulesTriageServiceClient;

    @Mock
    private DocumentManagementS3Client documentManagementClient;

    @InjectMocks
    private ClaimDataStandardizationOrchestrationService claimDataStandardizationOrchestrationService;

    private static final String CLAIM_ID = "claim-123";
    private static final String AUDIT_SCOPE_KEY = "auditScope";
    private static final String EXPECTED_SCOPE = "COMPREHENSIVE";

    @BeforeEach
    void setUp() {
        // Mocks initialized by MockitoExtension
    }

    @Test
    @DisplayName("Audit scope recognized")
    void audit_scope_recognized() {
        // Arrange
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put(AUDIT_SCOPE_KEY, EXPECTED_SCOPE);
        inputPayload.put("version", "1.0");
        inputPayload.put("claimType", "AUTO");

        // Mock infrastructure contract: Claim Data Store DynamoDB
        when(claimDataStoreClient.getItem(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("id", CLAIM_ID, "payload", inputPayload));

        // Mock infrastructure contract: Rules & Triage DynamoDB
        when(rulesTriageServiceClient.getItem(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("rulesVersion", "v2"));

        // Mock infrastructure contract: S3 Document Management
        when(documentManagementClient.putObject(anyString(), anyString(), anyString()))
                .thenReturn("s3://bucket/claim-123.json");

        // Act
        Map<String, Object> resultPayload = claimDataStandardizationOrchestrationService.transformAndOrchestrate(CLAIM_ID, inputPayload);

        // Assert
        assertNotNull(resultPayload, "Orchestration should return a payload");
        assertTrue(resultPayload.containsKey(AUDIT_SCOPE_KEY), "Audit scope should be recognized in result");
        assertEquals(EXPECTED_SCOPE, resultPayload.get(AUDIT_SCOPE_KEY), "Audit scope value should match input");

        // Verify infrastructure calls
        verify(claimDataStoreClient).getItem(anyString(), anyString(), anyString());
        verify(rulesTriageServiceClient).getItem(anyString(), anyString(), anyString());
        verify(documentManagementClient).putObject(anyString(), anyString(), anyString());
    }
}
