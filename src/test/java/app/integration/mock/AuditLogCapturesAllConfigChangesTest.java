package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

public class AuditLogCapturesAllConfigChangesTest {

    @Mock
    private AuditLogService auditLogService;

    private EnrichmentDecisionConfigService configService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        configService = new EnrichmentDecisionConfigService(auditLogService);
    }

    @Test
    void audit_log_captures_all_config_changes() {
        // Given: Simulated config changes matching the data model (id: string, payload: map)
        Map<String, Object> change1 = Map.of("id", "std-decision-001", "payload", Map.of("standardizationRule", "RULE_ALPHA", "enrichmentEnabled", true));
        Map<String, Object> change2 = Map.of("id", "std-decision-002", "payload", Map.of("decisionThreshold", 0.85, "fallbackAction", "REVIEW"));
        Map<String, Object> change3 = Map.of("id", "std-decision-003", "payload", Map.of("legacyMode", false, "auditLevel", "FULL"));

        // When: Apply multiple configuration changes to the enrichment decision module
        configService.applyConfigChange("Claim Data Standardization:enrichment:decision", change1);
        configService.applyConfigChange("Claim Data Standardization:enrichment:decision", change2);
        configService.applyConfigChange("Claim Data Standardization:enrichment:decision", change3);

        // Then: Verify that the audit log service captured all changes exactly once each
        verify(auditLogService, times(3)).recordConfigChange(eq("Claim Data Standardization:enrichment:decision"), any(Map.class));
        verifyNoMoreInteractions(auditLogService);
    }
}

interface AuditLogService {
    void recordConfigChange(String featureName, Map<String, Object> payload);
}

class EnrichmentDecisionConfigService {
    private final AuditLogService auditLogService;

    EnrichmentDecisionConfigService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    void applyConfigChange(String featureName, Map<String, Object> payload) {
        // Simulate configuration update logic
        auditLogService.recordConfigChange(featureName, payload);
    }
}
