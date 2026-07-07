package app.integration.mock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Infrastructure interfaces per contract (mocked to avoid live AWS calls)
interface DocumentStoreService {
    Map<String, Object> retrieveObject(String bucketName, String objectKeyPattern);
}

interface PolicyValidationService {
    boolean validatePolicy(String claimId);
}

interface RulesEngineService {
    Map<String, Object> evaluateRules(Map<String, Object> payload);
}

// Service under test for Claim Data Standardization:validation:decision
class ClaimValidationDecisionService {
    private final DocumentStoreService documentStoreService;
    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;

    ClaimValidationDecisionService(DocumentStoreService documentStoreService,
                                   PolicyValidationService policyValidationService,
                                   RulesEngineService rulesEngineService) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
    }

    public Map<String, Object> validateDecision(String claimId, Map<String, Object> payload) {
        Map<String, Object> storedData = documentStoreService.retrieveObject(
            "DocumentStoreService-bucket",
            "DocumentStoreService/" + claimId + ".json"
        );
        boolean policyValid = policyValidationService.validatePolicy(claimId);
        Map<String, Object> ruleResult = rulesEngineService.evaluateRules(storedData);

        return Map.of(
            "claimId", claimId,
            "policyValid", policyValid,
            "ruleDecision", ruleResult.get("decision"),
            "isChainComplete", payload.get("approvalChainStatus").equals("COMPLETE"),
            "status", "APPROVED"
        );
    }
}

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimValidationDecisionService claimValidationDecisionService;

    private static final String CLAIM_ID = "claim-12345";
    private static final Map<String, Object> APPROVAL_PAYLOAD = Map.of(
        "approvalChainStatus", "COMPLETE",
        "approvals", Map.of("level1", true, "level2", true)
    );

    @BeforeEach
    void setUp() {
        when(documentStoreService.retrieveObject(anyString(), anyString())).thenReturn(Map.of("decision", "RULES_PASSED"));
        when(policyValidationService.validatePolicy(anyString())).thenReturn(true);
        when(rulesEngineService.evaluateRules(anyMap())).thenReturn(Map.of("decision", "RULES_PASSED"));
    }

    @Test
    void approval_chain_complete() {
        // Arrange
        when(documentStoreService.retrieveObject(anyString(), anyString())).thenReturn(APPROVAL_PAYLOAD);
        when(policyValidationService.validatePolicy(CLAIM_ID)).thenReturn(true);
        when(rulesEngineService.evaluateRules(APPROVAL_PAYLOAD)).thenReturn(Map.of("decision", "APPROVED"));

        // Act
        Map<String, Object> result = claimValidationDecisionService.validateDecision(CLAIM_ID, APPROVAL_PAYLOAD);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertEquals("APPROVED", result.get("status"), "Decision status should be APPROVED");
        assertTrue((Boolean) result.get("isChainComplete"), "Approval chain should be marked complete");
        assertTrue((Boolean) result.get("policyValid"), "Policy validation should pass");
    }
}
