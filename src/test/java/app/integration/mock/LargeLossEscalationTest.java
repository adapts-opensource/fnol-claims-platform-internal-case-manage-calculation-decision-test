package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LargeLossEscalationTest {

    private static final Logger LOGGER = Logger.getLogger(LargeLossEscalationTest.class.getName());

    @Mock
    private DocumentStoreService_s3 documentStoreService;
    @Mock
    private PolicyValidationService_dynamodb policyValidationService;
    @Mock
    private RulesEngineService_dynamodb rulesEngineService;

    private ClaimTransformationEngine transformationEngine;

    @BeforeEach
    void setUp() {
        transformationEngine = new ClaimTransformationEngine(
            documentStoreService, policyValidationService, rulesEngineService, LOGGER
        );
    }

    @Test
    void transform_to_large_loss_on_high_severity() {
        // Arrange
        Map<String, Object> inputPayload = Map.of(
            "tenant_code", "FL01",
            "year", 2024,
            "severity", "complex",
            "estimated_loss_amount", 250000,
            "date_of_loss", "2024-11-01",
            "cause_of_loss", "wind",
            "product", "HO3"
        );

        // Mock infra I/O contracts per specification
        when(rulesEngineService.fetchRules("RulesEngineService_table", "pk"))
            .thenReturn(Map.of("threshold", 200000.0, "escalation_task", "Large Loss Escalation"));
        when(policyValidationService.validate("PolicyValidationService_table", "pk"))
            .thenReturn(Map.of("validation_status", "PASSED", "reserve_authority_limit", 500000.0));
        when(documentStoreService.storeDocument("DocumentStoreService-bucket", anyString(), anyMap()))
            .thenReturn("s3://DocumentStoreService-bucket/claims/claim-123.json");

        // Act
        Map<String, Object> result = transformationEngine.transform(inputPayload);

        // Assert Expected Results
        assertNotNull(result.get("claim_number"), "Claim number must be generated");
        assertTrue(result.get("claim_number").toString().startsWith("FL01-2024-"), 
            "Claim number should follow tenant-year format");
        assertEquals("Complex claim", result.get("claim_type"), "Claim type should be set to Complex claim");
        assertEquals("Large Loss Escalation", result.get("task_created"), 
            "Task Large Loss Escalation must be created");
        assertEquals("Claims supervisor or manager queue", result.get("routing_queue"), 
            "Must route to supervisor/manager queue");
        assertTrue((Boolean) result.get("reserve_authority_check_triggered"), 
            "Reserve authority check must be triggered");
        assertEquals("FL01", result.get("tenant_code"));
        assertEquals(2024, result.get("year"));
        assertNotNull(result.get("object_uri"), "S3 object URI must be resolved");

        // Verify infra I/O contracts were invoked exactly once
        verify(rulesEngineService).fetchRules("RulesEngineService_table", "pk");
        verify(policyValidationService).validate("PolicyValidationService_table", "pk");
        verify(documentStoreService).storeDocument(eq("DocumentStoreService-bucket"), anyString(), anyMap());
    }

    // Infra contract interfaces matching specification
    interface DocumentStoreService_s3 {
        String storeDocument(String bucketName, String objectKeyPattern, Map<String, Object> payload);
    }

    interface PolicyValidationService_dynamodb {
        Map<String, Object> validate(String tableName, String partitionKey);
    }

    interface RulesEngineService_dynamodb {
        Map<String, Object> fetchRules(String tableName, String partitionKey);
    }

    // Transformation logic encapsulation
    static class ClaimTransformationEngine {
        private final DocumentStoreService_s3 documentStoreService;
        private final PolicyValidationService_dynamodb policyValidationService;
        private final RulesEngineService_dynamodb rulesEngineService;
        private final Logger logger;

        ClaimTransformationEngine(DocumentStoreService_s3 documentStoreService,
                                  PolicyValidationService_dynamodb policyValidationService,
                                  RulesEngineService_dynamodb rulesEngineService,
                                  Logger logger) {
            this.documentStoreService = documentStoreService;
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
            this.logger = logger;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> transform(Map<String, Object> input) {
            // NFR: input_validation
            validateInput(input);

            // NFR: observability (structured_logging)
            logger.log(Level.INFO, "Starting claim transformation for tenant: {0}", input.get("tenant_code"));

            // Fetch rules & validate policy (Infra I/O contracts)
            Map<String, Object> rules = rulesEngineService.fetchRules("RulesEngineService_table", "pk");
            Map<String, Object> policyResult = policyValidationService.validate("PolicyValidationService_table", "pk");

            double threshold = (double) rules.getOrDefault("threshold", 100000.0);
            double lossAmount = (double) input.get("estimated_loss_amount");
            boolean exceedsThreshold = lossAmount >= threshold;

            // Transformation logic
            String claimNumber = String.format("%s-%d-CLM-%06d",
                input.get("tenant_code"), input.get("year"), Math.abs(UUID.randomUUID().hashCode() % 1000000));

            Map<String, Object> transformed = new java.util.HashMap<>();
            transformed.put("id", UUID.randomUUID().toString());
            transformed.put("claim_number", claimNumber);
            transformed.put("tenant_code", input.get("tenant_code"));
            transformed.put("year", input.get("year"));
            transformed.put("claim_type", exceedsThreshold ? "Complex claim" : "Standard claim");
            transformed.put("task_created", exceedsThreshold ? "Large Loss Escalation" : null);
            transformed.put("routing_queue", exceedsThreshold ? "Claims supervisor or manager queue" : "General Handler");
            transformed.put("reserve_authority_check_triggered", exceedsThreshold);
            transformed.put("date_of_loss", input.get("date_of_loss"));
            transformed.put("cause_of_loss", input.get("cause_of_loss"));
            transformed.put("product", input.get("product"));
            transformed.put("object_uri", null);

            // NFR: security (TLS in transit implied by S3 contract, secrets management via mock)
            String objectKey = String.format("DocumentStoreService/%s.json", transformed.get("id"));
            String uri = documentStoreService.storeDocument("DocumentStoreService-bucket", objectKey, transformed);
            transformed.put("object_uri", uri);

            // NFR: observability
            logger.log(Level.INFO, "Claim transformation completed. Claim#: {0}, Escalation: {1}",
                new Object[]{claimNumber, exceedsThreshold});

            return transformed;
        }

        private void validateInput(Map<String, Object> input) {
            if (input.get("tenant_code") == null || input.get("year") == null ||
                input.get("estimated_loss_amount") == null || input.get("date_of_loss") == null) {
                throw new IllegalArgumentException("Missing required fields for claim transformation");
            }
        }
    }
}
