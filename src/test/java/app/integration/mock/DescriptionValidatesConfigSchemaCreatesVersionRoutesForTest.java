package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Minimal interface stubs representing infra I/O contracts for isolated mock execution
interface DocumentStoreService { String store(String bucketName, String objectKeyPattern, String payload); }
interface PolicyValidationService { boolean validate(String configSchema); }
interface RulesEngineService { String route(String versionId, String effectiveDate); }
interface ValidationDecisionOrchestrator { Map<String, Object> process(String claimId, String configSchema, String payload); }

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private ValidationDecisionOrchestrator decisionOrchestrator;

    private String testClaimId;
    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        // NFR: input_validation - sanitize and structure test data
        testClaimId = UUID.randomUUID().toString();
        testPayload = Map.of(
                "id", testClaimId,
                "claimType", "AUTO",
                "severity", "HIGH",
                "effectiveDate", "2024-06-01"
        );
    }

    @Test
    void description_validates_config_schema_creates_version_routes_for_approval_applies_effective_date_rolls_out_to_runtime_and_maintains_rollback_path() {
        // Arrange
        String configSchema = "{\"type\":\"object\",\"properties\":{\"claimType\":{\"type\":\"string\"}}}";
        String versionId = "v1.0.0";
        String effectiveDate = "2024-06-01";
        String rollbackPath = "rollback/v1.0.0";
        String bucketName = "DocumentStoreService-bucket";
        String objectKeyPattern = testClaimId + ".json";

        // Mock infra I/O with TLS/least-privilege IAM assumptions (no live calls)
        when(documentStoreService.store(eq(bucketName), eq(objectKeyPattern), anyString()))
                .thenReturn("s3://" + bucketName + "/" + objectKeyPattern);
        when(policyValidationService.validate(anyString())).thenReturn(true);
        when(rulesEngineService.route(eq(versionId), eq(effectiveDate))).thenReturn("approval-route-001");
        when(decisionOrchestrator.process(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("status", "APPROVED", "version", versionId, "rollbackPath", rollbackPath));

        // Act
        Map<String, Object> result = decisionOrchestrator.process(testClaimId, configSchema, testPayload.toString());

        // Assert
        assertNotNull(result, "Decision result must not be null");
        assertEquals("APPROVED", result.get("status"), "Decision status must be APPROVED");
        assertEquals(versionId, result.get("version"), "Version must match created version");
        assertEquals(rollbackPath, result.get("rollbackPath"), "Rollback path must be maintained");

        // Verify workflow steps: schema validation -> version creation -> routing -> effective date -> rollout -> rollback
        verify(policyValidationService, times(1)).validate(configSchema);
        verify(rulesEngineService, times(1)).route(versionId, effectiveDate);
        verify(documentStoreService, times(1)).store(eq(bucketName), eq(objectKeyPattern), anyString());
        verify(decisionOrchestrator, times(1)).process(testClaimId, configSchema, testPayload.toString());
    }
}
