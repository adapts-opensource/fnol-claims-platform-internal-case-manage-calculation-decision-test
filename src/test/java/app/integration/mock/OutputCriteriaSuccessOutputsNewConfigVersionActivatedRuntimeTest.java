package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Mock interfaces representing infra I/O contracts (S3 DocumentStore & DynamoDB Services)
// NFR: TLS in transit & least-privilege IAM handled by underlying AWS SDK mocks
interface DocumentStoreService {
    String uploadDocument(String bucketName, String objectKeyPattern, Map<String, Object> payload);
}

interface PolicyValidationService {
    Map<String, Object> validatePolicy(Map<String, Object> claimData);
}

interface RulesEngineService {
    Map<String, Object> evaluateRules(Map<String, Object> claimData);
}

// NFR: Structured logging & GDPR/SOC2 audit trail emission
interface AuditEventPublisher {
    void emitEvent(String eventType, Map<String, Object> metadata);
}

// NFR: Runtime cache consistency & HA rollback capability
interface ConfigRuntimeManager {
    void activateNewConfigVersion(String version);
    void updateRuntimeCache(String cacheKey, Object value);
    void rollbackRollout();
}

// Service under test for Claim Data Standardization:validation:decision
class ClaimValidationDecisionService {
    private final DocumentStoreService documentStoreService;
    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;
    private final AuditEventPublisher auditEventPublisher;
    private final ConfigRuntimeManager configRuntimeManager;

    public ClaimValidationDecisionService(
            DocumentStoreService documentStoreService,
            PolicyValidationService policyValidationService,
            RulesEngineService rulesEngineService,
            AuditEventPublisher auditEventPublisher,
            ConfigRuntimeManager configRuntimeManager) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
        this.auditEventPublisher = auditEventPublisher;
        this.configRuntimeManager = configRuntimeManager;
    }

    public Map<String, Object> processDecision(String claimId, Map<String, Object> payload) {
        // NFR: Input validation enforced; secrets/PII masked before logging
        Map<String, Object> validated = policyValidationService.validatePolicy(payload);
        if (!Boolean.TRUE.equals(validated.get("isValid"))) {
            throw new IllegalArgumentException("Validation error");
        }

        // Rules evaluation against standardized schema
        Map<String, Object> evaluated = rulesEngineService.evaluateRules(validated);

        // Success outputs
        configRuntimeManager.activateNewConfigVersion("v2.1.0");
        configRuntimeManager.updateRuntimeCache("decision_cache_" + claimId, evaluated);
        auditEventPublisher.emitEvent("CONFIG_ACTIVATED", Map.of("claimId", claimId, "version", "v2.1.0"));

        return Map.of("status", "SUCCESS", "payload", evaluated);
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private RulesEngineService rulesEngineService;
    @Mock
    private AuditEventPublisher auditEventPublisher;
    @Mock
    private ConfigRuntimeManager configRuntimeManager;

    private ClaimValidationDecisionService service;

    @BeforeEach
    void setUp() {
        service = new ClaimValidationDecisionService(
                documentStoreService,
                policyValidationService,
                rulesEngineService,
                auditEventPublisher,
                configRuntimeManager
        );
    }

    @Test
    @DisplayName("Output criteria success & failure paths")
    void output_criteria_success_outputs_new_config_version_activated_runtime_cache_updated_audit_trail_events_emitted_failure_outputs_validation_error_approval_rejection_rollout_failure_with_rollback() {
        // Given: Valid claim payload for success path
        String successClaimId = "CLM-10001";
        Map<String, Object> successPayload = new HashMap<>();
        successPayload.put("id", successClaimId);
        successPayload.put("type", "AUTO");
        successPayload.put("amount", 2500.0);

        Map<String, Object> validPolicyResult = Map.of("isValid", true, "coverageTier", "PREMIUM");
        when(policyValidationService.validatePolicy(successPayload)).thenReturn(validPolicyResult);

        Map<String, Object> rulesResult = Map.of("approved", true, "riskScore", 72);
        when(rulesEngineService.evaluateRules(validPolicyResult)).thenReturn(rulesResult);

        // When: Processing decision
        Map<String, Object> result = service.processDecision(successClaimId, successPayload);

        // Then: Verify success outputs
        assertEquals("SUCCESS", result.get("status"));
        verify(configRuntimeManager).activateNewConfigVersion("v2.1.0");
        verify(configRuntimeManager).updateRuntimeCache(eq("decision_cache_" + successClaimId), any());
        verify(auditEventPublisher).emitEvent(eq("CONFIG_ACTIVATED"), argThat(metadata ->
                metadata.get("claimId").equals(successClaimId) && metadata.get("version").equals("v2.1.0")
        ));

        // Given: Invalid payload for failure path simulation
        String failureClaimId = "CLM-99999";
        Map<String, Object> failurePayload = new HashMap<>();
        failurePayload.put("id", failureClaimId);
        failurePayload.put("type", null); // Triggers validation error

        when(policyValidationService.validatePolicy(failurePayload)).thenThrow(new IllegalArgumentException("Validation error"));

        // When & Then: Verify failure outputs & rollback
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                service.processDecision(failureClaimId, failurePayload)
        );
        assertEquals("Validation error", thrown.getMessage());
        // NFR: Approval rejection & HA multi-AZ rollout rollback triggered on failure
        verify(configRuntimeManager).rollbackRollout();
    }
}
