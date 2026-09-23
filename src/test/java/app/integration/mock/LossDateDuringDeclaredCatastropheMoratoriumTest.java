package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimEnrichmentMockTest {

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimEnrichmentService(rulesEngineDecisionService, auditDiaryStore);
    }

    @Test
    void loss_date_during_declared_catastrophe_moratorium() {
        // Arrange
        String claimId = "CLM-1001";
        String lossDateStr = "2023-10-15";
        LocalDate lossDate = LocalDate.parse(lossDateStr);

        Map<String, Object> originalPayload = new HashMap<>();
        originalPayload.put("id", claimId);
        originalPayload.put("lossDate", lossDateStr);
        originalPayload.put("policyNumber", "POL-999");

        // Mock DynamoDB contract: RulesEngineDecisionService_dynamodb
        when(rulesEngineDecisionService.isDateInMoratorium(eq("RulesEngineDecisionService_table"), eq("pk"), any(Map.class)))
                .thenReturn(true);

        ArgumentCaptor<Map<String, Object>> auditPayloadCaptor = ArgumentCaptor.forClass(Map.class);

        // Act
        Map<String, Object> enrichedPayload = enrichmentService.enrich(originalPayload);

        // Assert
        assertTrue((Boolean) enrichedPayload.get("moratoriumFlag"), "Payload must contain moratorium flag when loss date is within declared catastrophe period");
        assertEquals("SUSPENDED_PENDING_REVIEW", enrichedPayload.get("decisionStatus"), "Decision status must be updated to pending review");
        assertEquals(claimId, enrichedPayload.get("id"), "Original claim ID must be preserved");

        // Verify DynamoDB interaction
        verify(rulesEngineDecisionService).isDateInMoratorium(eq("RulesEngineDecisionService_table"), eq("pk"), any(Map.class));

        // Verify S3 audit contract: AuditDiaryStore_s3
        verify(auditDiaryStore).writeAuditLog(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json"), auditPayloadCaptor.capture());
        assertNotNull(auditPayloadCaptor.getValue(), "Audit payload must not be null");
        assertTrue(auditPayloadCaptor.getValue().containsKey("enrichmentTimestamp"), "Audit log must include structured timestamp");
    }

    // Mocked Infra I/O Contracts
    private interface RulesEngineDecisionService {
        boolean isDateInMoratorium(String tableName, String partitionKey, Map<String, Object> query);
    }

    private interface AuditDiaryStore {
        String writeAuditLog(String bucketName, String objectKeyPattern, Map<String, Object> payload);
    }

    // Service under test (thread-safe, input-validating, structured-logging aware)
    private static class ClaimEnrichmentService {
        private final RulesEngineDecisionService rulesEngine;
        private final AuditDiaryStore auditStore;

        ClaimEnrichmentService(RulesEngineDecisionService rulesEngine, AuditDiaryStore auditStore) {
            this.rulesEngine = rulesEngine;
            this.auditStore = auditStore;
        }

        Map<String, Object> enrich(Map<String, Object> payload) {
            if (payload == null || !payload.containsKey("lossDate")) {
                throw new IllegalArgumentException("Input validation: payload and lossDate are required");
            }

            String lossDateStr = (String) payload.get("lossDate");
            LocalDate lossDate = LocalDate.parse(lossDateStr);
            Map<String, Object> query = new HashMap<>();
            query.put("lossDate", lossDateStr);

            boolean inMoratorium = rulesEngine.isDateInMoratorium("RulesEngineDecisionService_table", "pk", query);

            Map<String, Object> enriched = new HashMap<>(payload);
            enriched.put("moratoriumFlag", inMoratorium);

            if (inMoratorium) {
                enriched.put("decisionStatus", "SUSPENDED_PENDING_REVIEW");
            }

            // Simulate structured logging / audit trail
            Map<String, Object> auditPayload = new HashMap<>(enriched);
            auditPayload.put("enrichmentTimestamp", java.time.Instant.now().toString());
            auditStore.writeAuditLog("AuditDiaryStore-bucket", "AuditDiaryStore/" + payload.get("id") + ".json", auditPayload);

            return enriched;
        }
    }
}
