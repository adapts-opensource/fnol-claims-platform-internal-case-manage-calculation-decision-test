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
public class PurposeValidateNormalizeAndEnrichIncomingFnolDataTest {

    @Mock
    private FnolNormalizationService normalizationService;

    @Mock
    private AuditDiaryManager auditDiaryManager;

    @Mock
    private CentralDataStore centralDataStore;

    @Mock
    private SecureStorageService secureStorageService;

    private CaseManagementDecisionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CaseManagementDecisionProcessor(
                normalizationService,
                auditDiaryManager,
                centralDataStore,
                secureStorageService
        );
    }

    @Test
    void purpose_validate_normalize_and_enrich_incoming_fnol_data_before_persistence() {
        // Arrange: Simulate raw FNOL payload
        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("id", "ENG-4521");
        rawPayload.put("payload", Map.of(
                "claimId", "CLM-8890",
                "insuredId", "INS-7743",
                "incidentDate", "2023-11-15",
                "lossType", "COLLISION"
        ));

        // Mock normalized & enriched payload
        Map<String, Object> enrichedPayload = new HashMap<>();
        enrichedPayload.put("id", "ENG-4521");
        enrichedPayload.put("payload", Map.of(
                "claimId", "CLM-8890",
                "insuredId", "INS-7743",
                "incidentDate", "2023-11-15T00:00:00Z",
                "lossType", "COLLISION",
                "calculatedDecision", "APPROVE",
                "riskScore", 0.42,
                "enrichedAt", "2023-11-15T09:30:00Z"
        ));

        when(normalizationService.validateAndEnrich(rawPayload)).thenReturn(enrichedPayload);

        // Act: Process FNOL data before persistence
        Map<String, Object> result = processor.processBeforePersistence(rawPayload);

        // Assert: Verify validation, normalization, enrichment, and mock I/O contracts
        assertNotNull(result);
        assertEquals("ENG-4521", result.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultPayload = (Map<String, Object>) result.get("payload");
        assertEquals("APPROVE", resultPayload.get("calculatedDecision"));
        assertEquals("2023-11-15T00:00:00Z", resultPayload.get("incidentDate"));
        assertEquals("2023-11-15T09:30:00Z", resultPayload.get("enrichedAt"));
        assertEquals(0.42, resultPayload.get("riskScore"));

        // Verify external I/O interactions (mocked, no live AWS/HTTP calls)
        verify(normalizationService).validateAndEnrich(rawPayload);
        verify(auditDiaryManager).persistItem(eq("ENG-4521"), eq("Audit_Diary_Manager_dynamodb"), anyMap());
        verify(centralDataStore).putItem(eq("ENG-4521"), eq("Central_Data_Store_dynamodb"), anyMap());
        verify(secureStorageService).storeObject(eq("Secure_Storage-bucket"), eq("Secure_Storage/ENG-4521.json"), anyMap());
        verifyNoMoreInteractions(normalizationService, auditDiaryManager, centralDataStore, secureStorageService);
    }

    // Stub interfaces representing external infrastructure contracts
    interface FnolNormalizationService {
        Map<String, Object> validateAndEnrich(Map<String, Object> payload);
    }

    interface AuditDiaryManager {
        void persistItem(String partitionKey, String tableName, Map<String, Object> item);
    }

    interface CentralDataStore {
        void putItem(String partitionKey, String tableName, Map<String, Object> item);
    }

    interface SecureStorageService {
        String storeObject(String bucketName, String objectKeyPattern, Map<String, Object> data);
    }

    // Minimal service under test
    static class CaseManagementDecisionProcessor {
        private final FnolNormalizationService normalizationService;
        private final AuditDiaryManager auditDiaryManager;
        private final CentralDataStore centralDataStore;
        private final SecureStorageService secureStorageService;

        CaseManagementDecisionProcessor(FnolNormalizationService normalizationService,
                                        AuditDiaryManager auditDiaryManager,
                                        CentralDataStore centralDataStore,
                                        SecureStorageService secureStorageService) {
            this.normalizationService = normalizationService;
            this.auditDiaryManager = auditDiaryManager;
            this.centralDataStore = centralDataStore;
            this.secureStorageService = secureStorageService;
        }

        Map<String, Object> processBeforePersistence(Map<String, Object> rawFnolData) {
            Map<String, Object> enriched = normalizationService.validateAndEnrich(rawFnolData);
            String entityId = (String) enriched.get("id");
            auditDiaryManager.persistItem(entityId, "Audit_Diary_Manager_dynamodb", enriched);
            centralDataStore.putItem(entityId, "Central_Data_Store_dynamodb", enriched);
            secureStorageService.storeObject("Secure_Storage-bucket", "Secure_Storage/" + entityId + ".json", enriched);
            return enriched;
        }
    }
}
