package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the Claim Data Standardization:validation:decision feature.
 * Verifies that the approval workflow completes successfully when inputs are valid and constraints are met.
 * Includes checks for NFRs: input validation, security context, and structured result integrity.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowCompletesTest {

    @Mock
    private DocumentStoreService_s3 documentStoreService;

    @Mock
    private PolicyValidationService_dynamodb policyValidationService;

    @Mock
    private RulesEngineService_dynamodb rulesEngineService;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private ClaimDataStandardizationService claimService;

    @Test
    void approval_workflow_completes() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        String bucketName = "DocumentStoreService-bucket";
        String objectKeyPattern = "DocumentStoreService/%s.json".formatted(claimId);
        String tableName = "RulesEngineService_table";
        String partitionKey = "pk";

        Map<String, Object> payload = Map.of(
            "claimType", "AUTO",
            "amount", 2500.0,
            "status", "SUBMITTED",
            "metadata", Map.of("source", "APP", "version", "1.0")
        );

        ClaimDataStandardizationEntity entity = new ClaimDataStandardizationEntity(claimId, payload);
        Map<String, Object> decisionResult = Map.of(
            "decision", "APPROVED",
            "workflowStatus", "COMPLETED",
            "timestamp", System.currentTimeMillis()
        );

        when(documentStoreService.readObject(bucketName, objectKeyPattern)).thenReturn(entity);
        when(policyValidationService.validate(anyMap())).thenReturn(true);
        when(rulesEngineService.evaluateDecision(anyMap())).thenReturn(decisionResult);
        when(securityContext.hasRole("CLAIM_PROCESSOR")).thenReturn(true);

        // Act
        Map<String, Object> result = claimService.processDecision(claimId);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals("APPROVED", result.get("decision"), "Decision should be APPROVED");
        assertEquals("COMPLETED", result.get("workflowStatus"), "Workflow should be COMPLETED");

        // Verify Mock Interactions (Infra I/O Contracts)
        verify(documentStoreService).readObject(eq(bucketName), eq(objectKeyPattern));
        verify(policyValidationService).validate(anyMap());
        verify(rulesEngineService).evaluateDecision(anyMap());
        verify(securityContext).hasRole("CLAIM_PROCESSOR");

        // NFR: Input Validation & GDPR/SOC2
        // Ensure no PII is leaked in result payload (Model constraint: pii=false)
        boolean hasPii = result.values().stream()
            .anyMatch(v -> v instanceof String && ((String) v).matches(".*\\d{3}-\\d{2}-\\d{4}.*"));
        assertFalse(hasPii, "PII data must not be present in result payload");
        
        // NFR: Structured Logging / Observability
        // Verify result contains traceability fields
        assertTrue(result.containsKey("timestamp"), "Result must include timestamp for observability");
    }
}
