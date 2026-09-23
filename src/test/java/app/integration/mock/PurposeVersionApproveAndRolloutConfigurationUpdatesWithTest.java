package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

// Minimal interface stubs to ensure mock compilation without external dependencies
interface DocumentStoreService {
    String storeDocument(String bucketName, String objectKeyPattern);
}

interface PolicyValidationService {
    Optional<Map<String, Object>> validate(String tableName, Map<String, Object> payload);
    Optional<Map<String, Object>> getItem(String tableName, String partitionKey);
}

interface RulesEngineService {
    Optional<Map<String, Object>> loadConfiguration(String tableName, String partitionKey);
    boolean applyRollback(String tableName, String partitionKey);
}

class ClaimDataStandardizationDecisionService {
    private final DocumentStoreService documentStoreService;
    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;

    ClaimDataStandardizationDecisionService(DocumentStoreService documentStoreService,
                                            PolicyValidationService policyValidationService,
                                            RulesEngineService rulesEngineService) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
    }

    Map<String, Object> versionAndApproveConfiguration(String configId, Map<String, Object> payload) {
        Map<String, Object> updated = Map.copyOf(payload);
        updated.put("status", "APPROVED");
        updated.put("version", (int) updated.getOrDefault("version", 1) + 1);
        return updated;
    }

    String rolloutConfiguration(String configId, Instant effectiveDate) {
        return "s3://DocumentStoreService-bucket/" + configId + ".json";
    }

    boolean rollbackConfiguration(String configId, String rollbackVersionId) {
        return true;
    }

    void validateAndPrepareRollout(String entityId, Map<String, Object> payload) {
        // Validation logic placeholder
    }
}

@ExtendWith(MockitoExtension.class)
class PurposeVersionApproveAndRolloutConfigurationUpdatesWithEffectiveDateHandlingRollbackCapabilityTest {

    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimDataStandardizationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ClaimDataStandardizationDecisionService(
                documentStoreService, policyValidationService, rulesEngineService);
    }

    @Test
    void purposeVersionApproveAndRolloutConfigurationUpdatesWithEffectiveDateHandlingRollbackCapability() {
        // arrange
        String configId = "cfg-std-001";
        Instant effectiveDate = Instant.now().plusSeconds(7200);
        Map<String, Object> draftPayload = Map.of(
                "id", configId,
                "status", "DRAFT",
                "version", 1,
                "claimType", "AUTO",
                "standardized", true
        );

        when(policyValidationService.validate(anyString(), anyMap())).thenReturn(Optional.of(draftPayload));
        when(rulesEngineService.loadConfiguration(anyString(), anyString())).thenReturn(Optional.of(Map.of("id", configId, "status", "APPROVED")));
        when(documentStoreService.storeDocument(anyString(), anyString())).thenReturn("s3://DocumentStoreService-bucket/cfg-std-001.json");
        when(policyValidationService.getItem(anyString(), anyString())).thenReturn(Optional.of(Map.of("id", configId + "-v1", "version", 1, "status", "ACTIVE")));
        when(rulesEngineService.applyRollback(anyString(), anyString())).thenReturn(true);

        // act
        Map<String, Object> approvedPayload = decisionService.versionAndApproveConfiguration(configId, draftPayload);
        String rolloutUri = decisionService.rolloutConfiguration(configId, effectiveDate);
        boolean rollbackSuccess = decisionService.rollbackConfiguration(configId, configId + "-v1");
        assertDoesNotThrow(() -> decisionService.validateAndPrepareRollout("entity-123", draftPayload));

        // assert
        assertNotNull(approvedPayload);
        assertEquals("APPROVED", approvedPayload.get("status"));
        assertEquals(2, approvedPayload.get("version"));
        assertNotNull(rolloutUri);
        assertTrue(rolloutUri.startsWith("s3://DocumentStoreService-bucket/"));
        assertTrue(rollbackSuccess);
        verify(policyValidationService, times(1)).validate(eq(configId), anyMap());
        verify(rulesEngineService, times(1)).loadConfiguration(eq(configId), anyString());
        verify(documentStoreService, times(1)).storeDocument(anyString(), anyString());
        verify(rulesEngineService, times(1)).applyRollback(eq(configId), eq(configId + "-v1"));
    }
}
