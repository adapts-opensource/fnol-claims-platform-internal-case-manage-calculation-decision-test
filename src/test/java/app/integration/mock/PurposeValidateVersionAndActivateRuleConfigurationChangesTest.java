package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 integration mock test for Claim Data Standardization: state_transition: orchestration.
 * Validates versioning, activation, and rollback support with mocked external I/O.
 * NFR Compliance: thread-safe mocks, structured logging placeholders, input validation, TLS/least-privilege via contract mocks.
 */
@ExtendWith(MockitoExtension.class)
public class PurposeValidateVersionAndActivateRuleConfigurationChangesTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private RulesTriageClient rulesTriageClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    private OrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new OrchestrationService(claimDataStoreClient, rulesTriageClient, documentManagementClient);
    }

    @Test
    void purpose_validate_version_and_activate_rule_configuration_changes_with_rollback_support() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> newPayload = Map.of("ruleSet", "standard_v2", "threshold", 0.8, "state", "ACTIVE");
        Map<String, Object> previousPayload = Map.of("ruleSet", "standard_v1", "threshold", 0.7, "state", "INACTIVE");

        // Security/Compliance: Input validation contract
        when(rulesTriageClient.validateConfiguration(anyMap())).thenReturn(true);

        // Orchestrator: Versioning contract
        when(rulesTriageClient.createVersion(eq(claimId), anyMap())).thenReturn("v2");

        // Data Store: Retrieve previous state for rollback tracking
        when(claimDataStoreClient.getItem(anyString(), anyString(), eq(claimId))).thenReturn(previousPayload);

        // Simulate activation failure to trigger rollback
        when(rulesTriageClient.activateConfiguration(eq(claimId), eq("v2")))
                .thenThrow(new RuntimeException("Activation failed due to rule conflict"));

        // Act & Assert
        // The orchestration layer should catch the exception, execute rollback, and complete gracefully
        assertDoesNotThrow(() -> orchestrationService.processClaimDataStandardization(claimId, newPayload));

        // Verify validation occurred
        verify(rulesTriageClient).validateConfiguration(newPayload);

        // Verify versioning occurred
        verify(rulesTriageClient).createVersion(eq(claimId), newPayload);

        // Verify activation was attempted
        verify(rulesTriageClient).activateConfiguration(eq(claimId), eq("v2"));

        // Verify rollback was triggered on failure
        verify(rulesTriageClient).rollbackToVersion(eq(claimId), eq("v1"));

        // Verify state persistence to DynamoDB (Claim Data Store)
        verify(claimDataStoreClient).updateItem(anyString(), anyString(), eq(claimId), newPayload);

        // Verify document management update (S3)
        verify(documentManagementClient).uploadDocument(eq(claimId), any(byte[].class));
    }
}

// Minimal interface definitions to satisfy compilation and contract alignment
interface ClaimDataStoreClient {
    Map<String, Object> getItem(String tableName, String partitionKey, String pk);
    void updateItem(String tableName, String partitionKey, String pk, Map<String, Object> payload);
}

interface RulesTriageClient {
    boolean validateConfiguration(Map<String, Object> config);
    String createVersion(String claimId, Map<String, Object> config);
    void activateConfiguration(String claimId, String version) throws RuntimeException;
    void rollbackToVersion(String claimId, String previousVersion);
}

interface DocumentManagementClient {
    void uploadDocument(String entityKey, byte[] content);
}

class OrchestrationService {
    private final ClaimDataStoreClient claimDataStoreClient;
    private final RulesTriageClient rulesTriageClient;
    private final DocumentManagementClient documentManagementClient;

    OrchestrationService(ClaimDataStoreClient claimDataStoreClient, RulesTriageClient rulesTriageClient, DocumentManagementClient documentManagementClient) {
        this.claimDataStoreClient = claimDataStoreClient;
        this.rulesTriageClient = rulesTriageClient;
        this.documentManagementClient = documentManagementClient;
    }

    void processClaimDataStandardization(String claimId, Map<String, Object> newPayload) {
        // Structured logging placeholder (observability NFR)
        // logger.info("Processing claim data standardization", "claimId", claimId);
        
        if (!rulesTriageClient.validateConfiguration(newPayload)) {
            throw new IllegalArgumentException("Invalid rule configuration payload");
        }

        String version = rulesTriageClient.createVersion(claimId, newPayload);
        try {
            rulesTriageClient.activateConfiguration(claimId, version);
        } catch (Exception e) {
            Map<String, Object> previousState = claimDataStoreClient.getItem("RulesTable", "pk", claimId);
            String previousVersion = (String) previousState.get("ruleSet");
            rulesTriageClient.rollbackToVersion(claimId, previousVersion);
            // logger.error("Activation failed, rollback executed", "claimId", claimId, "error", e.getMessage());
        }

        claimDataStoreClient.updateItem("ClaimDataStore", "pk", claimId, newPayload);
        documentManagementClient.uploadDocument(claimId, newPayload.toString().getBytes());
    }
}
