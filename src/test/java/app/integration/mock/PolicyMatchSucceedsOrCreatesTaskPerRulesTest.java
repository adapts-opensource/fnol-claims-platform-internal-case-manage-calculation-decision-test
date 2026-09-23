package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
class PolicyMatchSucceedsOrCreatesTaskPerRulesTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @InjectMocks
    private ClaimDataValidationDecisionService claimDataValidationDecisionService;

    private String testClaimId = "claim-std-001";
    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = new HashMap<>();
        testPayload.put("id", testClaimId);
        testPayload.put("policyNumber", "POL-8821");
        testPayload.put("claimType", "AUTO");
        testPayload.put("lossDate", "2024-05-10");
    }

    @Test
    void policyMatchSucceedsOrCreatesTaskPerRules() {
        // Arrange: Mock policy validation to return a successful match
        when(policyValidationService.validatePolicy(eq(testClaimId), eq("POL-8821")))
                .thenReturn(Map.of("status", "MATCHED", "policyId", "POL-8821", "isActive", true));

        // Arrange: Mock rules engine to return decision with task creation instruction
        Map<String, Object> rulesDecision = Map.of(
                "decision", "APPROVE",
                "createTask", true,
                "taskId", "TASK-445",
                "taskType", "CLAIM_REVIEW"
        );
        when(rulesEngineService.evaluateRules(anyMap())).thenReturn(rulesDecision);

        // Act: Execute the standardization validation decision flow
        Map<String, Object> result = claimDataValidationDecisionService.processValidationDecision(testPayload);

        // Assert: Verify policy match succeeded or task creation was triggered per rules
        assertNotNull(result, "Result should not be null");
        assertTrue((Boolean) result.getOrDefault("policyMatchSucceeded", false), "Policy match should succeed");
        assertTrue((Boolean) result.getOrDefault("taskCreated", false), "Task should be created per rules");
        assertEquals("TASK-445", result.get("taskId"), "Task ID should match rules engine output");

        // Verify infra interactions
        verify(policyValidationService).validatePolicy(eq(testClaimId), eq("POL-8821"));
        verify(rulesEngineService).evaluateRules(anyMap());
        verify(documentStoreService).storeDocument(eq("DocumentStoreService-bucket"), eq("claim-std-001.json"), anyString());
    }
}

// Minimal infra interfaces for mock compilation
interface PolicyValidationService { Map<String, Object> validatePolicy(String claimId, String policyNumber); }
interface RulesEngineService { Map<String, Object> evaluateRules(Map<String, Object> payload); }
interface DocumentStoreService { void storeDocument(String bucketName, String objectKey, String payloadJson); }
interface ClaimDataValidationDecisionService { Map<String, Object> processValidationDecision(Map<String, Object> payload); }
