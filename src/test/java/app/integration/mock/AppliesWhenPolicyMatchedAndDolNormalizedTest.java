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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Minimal interface stubs for compilation context of mocked infra contracts
interface AuditDiaryStoreClient {
    String putObject(String bucketName, String objectKey, String payload);
}

interface RulesEngineDecisionClient {
    Map<String, Object> getItem(String tableName, String partitionKey);
}

interface WorkflowTaskRouterClient {
    Map<String, Object> getItem(String tableName, String partitionKey);
}

interface ClaimDataStandardizationCalculationTransformer {
    Map<String, Object> transform(String id, Map<String, Object> payload);
}

@ExtendWith(MockitoExtension.class)
public class AppliesWhenPolicyMatchedAndDolNormalizedTest {

    @Mock
    private AuditDiaryStoreClient auditDiaryStoreClient;

    @Mock
    private RulesEngineDecisionClient rulesEngineDecisionClient;

    @Mock
    private WorkflowTaskRouterClient workflowTaskRouterClient;

    @InjectMocks
    private ClaimDataStandardizationCalculationTransformer transformer;

    private String claimId;
    private Map<String, Object> inputPayload;

    @BeforeEach
    void setUp() {
        claimId = "CLM-98765-STD";
        inputPayload = new HashMap<>();
        inputPayload.put("policyMatched", true);
        inputPayload.put("dateOfLoss", "12/31/2023"); // Raw/unnormalized format
    }

    @Test
    @DisplayName("applies_when_policy_matched_and_dol_normalized")
    void applies_when_policy_matched_and_dol_normalized() {
        // Arrange
        Map<String, Object> expectedStandardizedPayload = new HashMap<>();
        expectedStandardizedPayload.put("id", claimId);
        expectedStandardizedPayload.put("policyMatched", true);
        expectedStandardizedPayload.put("dateOfLoss", "2023-12-31T00:00:00Z"); // ISO 8601 normalized
        expectedStandardizedPayload.put("standardizationStatus", "APPLIED");

        // Mock infra I/O contracts (S3 & DynamoDB)
        when(rulesEngineDecisionClient.getItem(anyString(), anyString())).thenReturn(Map.of("decisionCode", "MATCHED"));
        when(workflowTaskRouterClient.getItem(anyString(), anyString())).thenReturn(Map.of("nextStep", "CALCULATE"));
        when(auditDiaryStoreClient.putObject(anyString(), anyString(), anyString())).thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/" + claimId + ".json");

        // Act
        Map<String, Object> result = transformer.transform(claimId, inputPayload);

        // Assert payload transformation
        assertNotNull(result, "Transformed payload should not be null");
        assertEquals(claimId, result.get("id"), "ID should match input");
        assertEquals(true, result.get("policyMatched"), "Policy match flag should be preserved");
        assertEquals("2023-12-31T00:00:00Z", result.get("dateOfLoss"), "DOL should be normalized to ISO 8601");
        assertEquals("APPLIED", result.get("standardizationStatus"), "Standardization status should reflect application");

        // Verify infrastructure interactions
        verify(rulesEngineDecisionClient).getItem(anyString(), anyString());
        verify(workflowTaskRouterClient).getItem(anyString(), anyString());
        verify(auditDiaryStoreClient).putObject(
            eq("AuditDiaryStore-bucket"),
            eq("AuditDiaryStore/" + claimId + ".json"),
            anyString()
        );
    }
}
