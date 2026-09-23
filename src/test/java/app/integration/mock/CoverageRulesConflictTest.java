package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

// Infra contract interfaces for mocking external I/O (S3 & DynamoDB)
interface DocumentStoreService {
    String storeResult(String entityId, Map<String, Object> payload);
}

interface PolicyValidationService {
    Map<String, Object> validatePolicy(String policyId);
}

interface RulesEngineService {
    Map<String, Object> fetchRules(String coverageType);
}

class ClaimDataStandardizationDecisionValidator {
    private final DocumentStoreService documentStoreService;
    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;

    ClaimDataStandardizationDecisionValidator(DocumentStoreService documentStoreService,
                                              PolicyValidationService policyValidationService,
                                              RulesEngineService rulesEngineService) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
    }

    String evaluateDecision(String claimId, Map<String, Object> payload) {
        String coverageType = (String) payload.get("coverageType");
        Map<String, Object> rules = rulesEngineService.fetchRules(coverageType);
        Map<String, Object> policy = policyValidationService.validatePolicy("POL-DEFAULT");

        String status = (String) rules.get("status");
        if ("CONFLICT".equals(status)) {
            Map<String, Object> decisionPayload = new HashMap<>();
            decisionPayload.put("id", claimId);
            decisionPayload.put("decision", "REJECTED");
            decisionPayload.put("reason", "Coverage rules conflict detected");
            documentStoreService.storeResult(claimId, decisionPayload);
            return "REJECTED: Coverage rules conflict";
        }
        return "APPROVED";
    }
}

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimDataStandardizationDecisionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClaimDataStandardizationDecisionValidator(documentStoreService, policyValidationService, rulesEngineService);
    }

    @Test
    void coverageRulesConflict() {
        // Arrange
        String claimId = "CLM-12345";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("coverageType", "AUTO");

        Map<String, Object> conflictingRules = new HashMap<>();
        conflictingRules.put("ruleId", "COV-RULE-1");
        conflictingRules.put("status", "CONFLICT");
        conflictingRules.put("message", "Coverage limits exceed policy maximums");

        Map<String, Object> policyDetails = new HashMap<>();
        policyDetails.put("policyId", "POL-98765");
        policyDetails.put("maxCoverage", 10000);

        when(rulesEngineService.fetchRules("AUTO")).thenReturn(conflictingRules);
        when(policyValidationService.validatePolicy("POL-DEFAULT")).thenReturn(policyDetails);

        // Act
        String decision = validator.evaluateDecision(claimId, payload);

        // Assert
        assertNotNull(decision);
        assertTrue(decision.contains("CONFLICT"));
        verify(rulesEngineService).fetchRules("AUTO");
        verify(policyValidationService).validatePolicy("POL-DEFAULT");
        verify(documentStoreService).storeResult(eq(claimId), any(Map.class));
    }
}
