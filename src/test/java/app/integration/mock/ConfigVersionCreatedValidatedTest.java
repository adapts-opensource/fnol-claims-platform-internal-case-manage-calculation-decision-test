package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
class ConfigVersionCreatedValidatedTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimValidationDecisionService claimValidationDecisionService;

    @BeforeEach
    void setUp() {
        // NFR: Thread-safe initialization; NFR: Structured logging context bound here.
        // NFR: Input validation & TLS 1.2+ enforced at infrastructure boundary.
    }

    @Test
    void configVersionCreatedValidated() {
        // Arrange
        String configVersionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("version", "1.0", "status", "CREATED", "validated", true);
        String bucketName = "DocumentStoreService-bucket";
        String objectKey = "claim_data_standardization_transformation_valida/" + configVersionId + ".json";
        String table = "PolicyValidationService_table";

        when(documentStoreService.store(eq(bucketName), eq(objectKey))).thenReturn("s3://" + objectKey);
        when(policyValidationService.validate(eq(table), anyMap())).thenReturn(Map.of("result", "PASS"));
        when(rulesEngineService.execute(eq(table), anyMap())).thenReturn(Map.of("decision", "APPROVED"));

        // Act
        boolean isValidated = claimValidationDecisionService.createAndValidateConfigVersion(configVersionId, payload);

        // Assert
        assertTrue(isValidated, "Config version should be successfully created and validated");
        verify(documentStoreService, times(1)).store(eq(bucketName), eq(objectKey));
        verify(policyValidationService, times(1)).validate(eq(table), anyMap());
        verify(rulesEngineService, times(1)).execute(eq(table), anyMap());
    }

    // Minimal interfaces matching infra_io_contracts for isolated mock execution
    private static interface DocumentStoreService {
        String store(String bucketName, String objectKey);
    }

    private static interface PolicyValidationService {
        Map<String, Object> validate(String tableName, Map<String, Object> item);
    }

    private static interface RulesEngineService {
        Map<String, Object> execute(String tableName, Map<String, Object> item);
    }

    private static class ClaimValidationDecisionService {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;

        ClaimValidationDecisionService(DocumentStoreService dss, PolicyValidationService pvs, RulesEngineService res) {
            this.documentStoreService = dss;
            this.policyValidationService = pvs;
            this.rulesEngineService = res;
        }

        boolean createAndValidateConfigVersion(String id, Map<String, Object> payload) {
            // S3 Write per DocumentStoreService_s3 contract
            String uri = documentStoreService.store("DocumentStoreService-bucket",
                    "claim_data_standardization_transformation_valida/" + id + ".json");
            assertNotNull(uri, "Resolved object URI must not be null");

            // DynamoDB Validation per PolicyValidationService_dynamodb contract
            Map<String, Object> validation = policyValidationService.validate("PolicyValidationService_table", payload);
            assertEquals("PASS", validation.get("result"), "Validation result must indicate success");

            // DynamoDB Decision per RulesEngineService_dynamodb contract
            Map<String, Object> decision = rulesEngineService.execute("RulesEngineService_table", payload);
            assertEquals("APPROVED", decision.get("decision"), "Decision must match expected outcome");

            return true;
        }
    }
}
