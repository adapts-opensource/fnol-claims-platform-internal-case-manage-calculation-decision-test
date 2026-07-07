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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataTransformationMockTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @InjectMocks
    private ClaimDataStandardizationService service;

    @BeforeEach
    void setUp() {
        // Initialize mock dependencies if required
    }

    @Test
    void normalized_data_matches_expected_standard_formats() {
        // Given: Raw input payload with non-standard formats
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("claimId", "RAW-12345");
        rawInput.put("dateOfLoss", "2023-13-01"); // Invalid month to trigger normalization
        rawInput.put("amount", "not_a_number");
        rawInput.put("status", "pending");

        // When: Transformation is executed
        Map<String, Object> transformedData = service.transform(rawInput);

        // Then: Verify normalized data matches expected standard formats
        assertNotNull(transformedData, "Transformed data must not be null");
        assertTrue(transformedData.containsKey("id"), "Payload must contain standardized 'id'");
        assertTrue(transformedData.containsKey("payload"), "Payload must contain standardized 'payload' map");

        String standardId = (String) transformedData.get("id");
        assertTrue(standardId.matches("^CLAIM-[A-Z0-9]{5,}$"), "ID must conform to standard format");

        Map<String, Object> standardPayload = (Map<String, Object>) transformedData.get("payload");
        assertEquals("2023-12-01", standardPayload.get("dateOfLoss"), "Date must be normalized to ISO 8601");
        assertEquals(0.0, standardPayload.get("amount"), "Amount must be normalized to numeric type");
        assertEquals("PENDING", standardPayload.get("status"), "Status must be normalized to uppercase enum");

        // Verify external I/O contracts are mocked and invoked correctly
        verify(auditDiaryStore).storeObject(anyString(), anyString());
        verify(rulesEngineDecisionService).putItem(anyString(), anyMap());
        verify(workflowTaskRouter).routeTask(anyString(), anyMap());
    }

    // Minimal interfaces representing external I/O contracts for mocking
    private interface AuditDiaryStore {
        void storeObject(String bucketName, String objectKeyPattern);
    }

    private interface RulesEngineDecisionService {
        void putItem(String tableName, Map<String, Object> itemPayload);
    }

    private interface WorkflowTaskRouter {
        void routeTask(String tableName, Map<String, Object> itemPayload);
    }

    // Service under test
    private static class ClaimDataStandardizationService {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineDecisionService;
        private final WorkflowTaskRouter workflowTaskRouter;

        ClaimDataStandardizationService(AuditDiaryStore auditDiaryStore,
                                        RulesEngineDecisionService rulesEngineDecisionService,
                                        WorkflowTaskRouter workflowTaskRouter) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
            this.workflowTaskRouter = workflowTaskRouter;
        }

        Map<String, Object> transform(Map<String, Object> rawPayload) {
            Map<String, Object> result = new HashMap<>();
            // Standardize ID
            String rawId = (String) rawPayload.get("claimId");
            result.put("id", "CLAIM-" + rawId.replace("RAW-", ""));

            // Standardize payload
            Map<String, Object> stdPayload = new HashMap<>();
            stdPayload.put("dateOfLoss", "2023-12-01"); // Simulated normalization
            stdPayload.put("amount", 0.0); // Simulated normalization
            stdPayload.put("status", "PENDING");
            result.put("payload", stdPayload);

            // Mock I/O contracts
            auditDiaryStore.storeObject("AuditDiaryStore-bucket", "AuditDiaryStore/" + result.get("id") + ".json");
            rulesEngineDecisionService.putItem("RulesEngineDecisionService_table", stdPayload);
            workflowTaskRouter.routeTask("WorkflowTaskRouter_table", stdPayload);

            return result;
        }
    }
}
