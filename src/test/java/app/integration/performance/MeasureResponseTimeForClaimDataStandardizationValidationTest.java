package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationPerformanceTest {

    private static final Logger logger = Logger.getLogger(ClaimDataStandardizationValidationPerformanceTest.class.getName());

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimDataStandardizationValidator validator;

    @BeforeEach
    void setUp() {
        when(documentStoreService.getObject(anyString(), anyString())).thenReturn(Map.of("status", "stored"));
        when(policyValidationService.getItem(anyString(), anyString())).thenReturn(Map.of("valid", true));
        when(rulesEngineService.getItem(anyString(), anyString())).thenReturn(Map.of("decision", "APPROVED"));

        validator = new ClaimDataStandardizationValidator(documentStoreService, policyValidationService, rulesEngineService);
    }

    @Test
    @DisplayName("measure_response_time_for_claim_data_standardization_validation_decision_under_nominal_load")
    void measure_response_time_for_claim_data_standardization_validation_decision_under_nominal_load() {
        String claimId = "claim-123";
        Map<String, Object> payload = Map.of("claimType", "AUTO", "amount", 1500.0, "status", "NEW");

        long startTime = System.nanoTime();
        try {
            Map<String, Object> result = validator.validateDecision(claimId, payload);
            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Structured logging for observability NFR
            logger.log(Level.INFO, String.format(
                    "{\"event\":\"performance_test\",\"test\":\"%s\",\"duration_ms\":%d,\"claimId\":\"%s\",\"result\":\"%s\"}",
                    "measure_response_time_for_claim_data_standardization_validation_decision_under_nominal_load",
                    durationMs, claimId, result));

            // Performance gate: nominal load must complete within threshold
            assertTrue(durationMs < 500, "Response time exceeded nominal threshold: " + durationMs + "ms");
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format(
                    "{\"event\":\"performance_test_error\",\"test\":\"%s\",\"error\":\"%s\"}",
                    "measure_response_time_for_claim_data_standardization_validation_decision_under_nominal_load",
                    e.getMessage()));
            fail("Validation failed unexpectedly: " + e.getMessage());
        }
    }

    // Mocked infra contracts to avoid live AWS/HTTP calls
    interface DocumentStoreService {
        Map<String, Object> getObject(String bucketName, String objectKey);
    }

    interface PolicyValidationService {
        Map<String, Object> getItem(String tableName, String partitionKey);
    }

    interface RulesEngineService {
        Map<String, Object> getItem(String tableName, String partitionKey);
    }

    // Simplified domain validator for test execution
    static class ClaimDataStandardizationValidator {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;

        ClaimDataStandardizationValidator(DocumentStoreService documentStoreService,
                                          PolicyValidationService policyValidationService,
                                          RulesEngineService rulesEngineService) {
            this.documentStoreService = documentStoreService;
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
        }

        Map<String, Object> validateDecision(String id, Map<String, Object> payload) {
            // Simulate nominal sequential I/O calls
            documentStoreService.getObject("DocumentStoreService-bucket", id + ".json");
            policyValidationService.getItem("PolicyValidationService_table", "pk");
            rulesEngineService.getItem("RulesEngineService_table", "pk");
            return Map.of("decision", "APPROVED", "standardized", true);
        }
    }
}
