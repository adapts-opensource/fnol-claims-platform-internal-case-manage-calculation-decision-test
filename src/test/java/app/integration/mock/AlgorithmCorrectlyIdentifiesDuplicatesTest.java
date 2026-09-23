package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AlgorithmCorrectlyIdentifiesDuplicatesTest {

    @Mock
    private AuditDiaryStoreS3Client mockS3Client;

    @Mock
    private RulesEngineDecisionDynamoDbClient mockRulesEngineClient;

    @Mock
    private WorkflowTaskRouterDynamoDbClient mockWorkflowRouterClient;

    private ClaimDataStandardizationCalculationTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // Inject mocked infrastructure to guarantee zero live I/O during transformation
        transformationService = new ClaimDataStandardizationCalculationTransformationService(
                mockS3Client,
                mockRulesEngineClient,
                mockWorkflowRouterClient
        );
    }

    @Test
    void algorithm_correctly_identifies_duplicates() {
        // Arrange: Prepare claim data with intentional duplicates based on payload claimId
        Map<String, Object> payloadUnique = Map.of("claimId", "CLM-9921", "amount", 1250.0, "type", "AUTO");
        Map<String, Object> payloadDuplicate = Map.of("claimId", "CLM-9921", "amount", 1250.0, "type", "AUTO");
        Map<String, Object> payloadAnotherUnique = Map.of("claimId", "CLM-8844", "amount", 4300.0, "type", "HOME");

        List<ClaimDataStandardizationCalculationTransform> inputClaims = List.of(
                new ClaimDataStandardizationCalculationTransform("TRANS-001", payloadUnique),
                new ClaimDataStandardizationCalculationTransform("TRANS-002", payloadDuplicate),
                new ClaimDataStandardizationCalculationTransform("TRANS-003", payloadAnotherUnique)
        );

        // Act: Run transformation algorithm
        List<String> identifiedDuplicates = transformationService.identifyDuplicateClaimIds(inputClaims);

        // Assert: Verify duplicate detection logic
        assertEquals(1, identifiedDuplicates.size(), "Algorithm should identify exactly one duplicate claim ID");
        assertTrue(identifiedDuplicates.contains("CLM-9921"), "CLM-9921 must be flagged as duplicate");
        assertFalse(identifiedDuplicates.contains("CLM-8844"), "Unique claim CLM-8844 must not be flagged");

        // Verify strict isolation: no external I/O contracts invoked during pure transformation
        verifyNoInteractions(mockS3Client, mockRulesEngineClient, mockWorkflowRouterClient);
    }

    // --- Supporting Domain Model ---
    static class ClaimDataStandardizationCalculationTransform {
        private final String id;
        private final Map<String, Object> payload;

        public ClaimDataStandardizationCalculationTransform(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }

        public String getId() { return id; }
        public Map<String, Object> getPayload() { return payload; }
    }

    // --- Mocked Infrastructure Interfaces ---
    interface AuditDiaryStoreS3Client {
        String storeAuditRecord(String bucketName, String objectKey, String content);
    }

    interface RulesEngineDecisionDynamoDbClient {
        Map<String, Object> queryRules(String tableName, String partitionKey, String keyCondition);
    }

    interface WorkflowTaskRouterDynamoDbClient {
        Map<String, Object> routeTask(String tableName, String partitionKey, String taskData);
    }

    // --- Service Under Test ---
    static class ClaimDataStandardizationCalculationTransformationService {
        private final AuditDiaryStoreS3Client s3Client;
        private final RulesEngineDecisionDynamoDbClient rulesEngineClient;
        private final WorkflowTaskRouterDynamoDbClient workflowRouterClient;

        public ClaimDataStandardizationCalculationTransformationService(
                AuditDiaryStoreS3Client s3Client,
                RulesEngineDecisionDynamoDbClient rulesEngineClient,
                WorkflowTaskRouterDynamoDbClient workflowRouterClient) {
            this.s3Client = s3Client;
            this.rulesEngineClient = rulesEngineClient;
            this.workflowRouterClient = workflowRouterClient;
        }

        /**
         * Core transformation algorithm: groups claims by payload claimId,
         * filters groups with count > 1, and extracts duplicate IDs.
         * NFR: Input validation & thread-safety via immutable streams.
         */
        public List<String> identifyDuplicateClaimIds(List<ClaimDataStandardizationCalculationTransform> claims) {
            if (claims == null || claims.isEmpty()) {
                return List.of();
            }
            return claims.stream()
                    .map(transform -> (String) transform.getPayload().get("claimId"))
                    .filter(claimId -> claimId != null && !claimId.isBlank())
                    .collect(Collectors.groupingBy(id -> id, Collectors.counting()))
                    .entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }
}
