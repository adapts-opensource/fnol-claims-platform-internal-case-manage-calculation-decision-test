package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportImmutableTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimDataStandardizationService claimDataStandardizationService;

    @BeforeEach
    void setUp() {
        claimDataStandardizationService = new ClaimDataStandardizationService(
                documentStoreService, policyValidationService, rulesEngineService
        );
    }

    @Test
    void export_immutable() {
        // Arrange
        String claimId = "claim-789";
        Map<String, Object> originalPayload = new HashMap<>();
        originalPayload.put("policyNumber", "POL-2024-001");
        originalPayload.put("status", "VALIDATED");
        originalPayload.put("decision", "APPROVED");

        when(policyValidationService.fetchItem(anyString(), anyString())).thenReturn(originalPayload);
        when(rulesEngineService.fetchItem(anyString(), anyString())).thenReturn(Collections.emptyMap());
        when(documentStoreService.writeObject(anyString(), anyString(), anyMap())).thenReturn("s3://DocumentStoreService-bucket/claim-789.json");

        // Act
        String exportUri = claimDataStandardizationService.exportDecisionData(claimId, originalPayload);

        // Assert
        assertNotNull(exportUri, "Export URI should not be null");
        assertTrue(exportUri.startsWith("s3://"), "Export URI should point to S3");

        // Verify immutability: exported payload must be unmodifiable
        Map<String, Object> exportedPayload = claimDataStandardizationService.getExportedPayload(claimId);
        assertThrows(UnsupportedOperationException.class, () -> exportedPayload.put("tampered", "true"),
                "Exported payload must be immutable");

        // Verify infrastructure interactions
        verify(documentStoreService).writeObject(eq("DocumentStoreService-bucket"), eq("claim-789.json"), anyMap());
        verify(policyValidationService).fetchItem(eq("PolicyValidationService_table"), eq("pk"));
        verify(rulesEngineService).fetchItem(eq("RulesEngineService_table"), eq("pk"));
    }

    // Minimal service stub to demonstrate the tested logic
    static class ClaimDataStandardizationService {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;
        private final Map<String, Map<String, Object>> exportedData = Collections.synchronizedMap(new HashMap<>());

        ClaimDataStandardizationService(DocumentStoreService documentStoreService,
                                        PolicyValidationService policyValidationService,
                                        RulesEngineService rulesEngineService) {
            this.documentStoreService = documentStoreService;
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
        }

        String exportDecisionData(String claimId, Map<String, Object> payload) {
            // Fetch validation rules and policy context
            Map<String, Object> validationContext = policyValidationService.fetchItem("PolicyValidationService_table", "pk");
            Map<String, Object> ruleContext = rulesEngineService.fetchItem("RulesEngineService_table", "pk");

            // Standardize and freeze payload for export
            Map<String, Object> standardizedPayload = new HashMap<>(payload);
            standardizedPayload.put("validationContext", validationContext);
            standardizedPayload.put("ruleContext", ruleContext);
            standardizedPayload.put("exportedAt", System.currentTimeMillis());

            // Store immutable snapshot
            exportedData.put(claimId, Collections.unmodifiableMap(new HashMap<>(standardizedPayload)));

            // Write to S3
            return documentStoreService.writeObject("DocumentStoreService-bucket", claimId + ".json", standardizedPayload);
        }

        Map<String, Object> getExportedPayload(String claimId) {
            return exportedData.getOrDefault(claimId, Collections.emptyMap());
        }
    }

    // Mock interfaces matching infra contracts
    interface DocumentStoreService {
        String writeObject(String bucketName, String objectKeyPattern, Map<String, Object> payload);
    }

    interface PolicyValidationService {
        Map<String, Object> fetchItem(String tableName, String partitionKey);
    }

    interface RulesEngineService {
        Map<String, Object> fetchItem(String tableName, String partitionKey);
    }
}
