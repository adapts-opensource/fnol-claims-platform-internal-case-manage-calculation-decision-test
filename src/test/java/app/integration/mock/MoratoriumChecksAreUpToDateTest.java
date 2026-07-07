package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    private ClaimDataStandardizationTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new ClaimDataStandardizationTransformer(auditDiaryStore, rulesEngineService);
    }

    @Test
    void moratoriumChecksAreUpToDate() {
        // Given: Input payload representing a claim awaiting standardization
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", "FNOL-2024-001");
        inputPayload.put("moratoriumChecks", Map.of(
                "lastChecked", "2024-05-20",
                "status", "REQUIRES_VALIDATION",
                "appliedRules", Map.of("legalHold", true, "maintenanceWindow", false)
        ));
        inputPayload.put("metadata", Map.of("source", "FNOL_Claims_Platform", "version", "1.0"));

        // When: Transformation is executed
        Map<String, Object> resultPayload = transformer.transform(inputPayload);

        // Then: Verify data standardization correctly updates moratorium checks
        assertNotNull(resultPayload, "Transformed payload must not be null");
        assertEquals("FNOL-2024-001", resultPayload.get("id"), "Claim identifier must be preserved");

        @SuppressWarnings("unchecked")
        Map<String, Object> moratoriumData = (Map<String, Object>) resultPayload.get("moratoriumChecks");
        assertNotNull(moratoriumData, "Moratorium checks payload must exist");
        assertEquals("UP_TO_DATE", moratoriumData.get("status"), "Status should be standardized to UP_TO_DATE");
        assertEquals("2024-05-20", moratoriumData.get("lastChecked"), "Timestamp must be retained");

        // Verify mocked infrastructure contracts are invoked exactly once
        verify(auditDiaryStore, times(1)).writePayload("AuditDiaryStore-bucket", "AuditDiaryStore/FNOL-2024-001.json");
        verify(rulesEngineService, times(1)).evaluateDecision("RulesEngineDecisionService_table", inputPayload);
    }

    /**
     * Service responsible for Claim Data Standardization:decision:transformation.
     * Encapsulates business logic and delegates to mocked infrastructure contracts.
     */
    static class ClaimDataStandardizationTransformer {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineService;

        ClaimDataStandardizationTransformer(AuditDiaryStore auditDiaryStore, RulesEngineDecisionService rulesEngineService) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineService = rulesEngineService;
        }

        Map<String, Object> transform(Map<String, Object> payload) {
            // Standardize moratorium checks status
            @SuppressWarnings("unchecked")
            Map<String, Object> checks = (Map<String, Object>) payload.getOrDefault("moratoriumChecks", new HashMap<>());
            checks.put("status", "UP_TO_DATE");
            payload.put("moratoriumChecks", checks);

            // Delegate to mocked infra I/O contracts
            String claimId = String.valueOf(payload.get("id"));
            auditDiaryStore.writePayload("AuditDiaryStore-bucket", "AuditDiaryStore/" + claimId + ".json");
            rulesEngineService.evaluateDecision("RulesEngineDecisionService_table", payload);

            return payload;
        }
    }

    /**
     * Mock interface for S3 AuditDiaryStore logical contract.
     */
    interface AuditDiaryStore {
        void writePayload(String bucketName, String objectKey);
    }

    /**
     * Mock interface for DynamoDB RulesEngineDecisionService logical contract.
     */
    interface RulesEngineDecisionService {
        Map<String, Object> evaluateDecision(String tableName, Map<String, Object> payload);
    }
}
