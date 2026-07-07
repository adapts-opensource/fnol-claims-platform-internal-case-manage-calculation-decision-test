package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Minimal interface definitions representing external I/O contracts
interface DocumentStoreService_s3 {
    Map<String, Object> retrieveObjectPayload(String objectKey);
}

interface PolicyValidationService_dynamodb {
    Map<String, Object> fetchPolicyItemByAddress(String address);
}

interface RulesEngineService_dynamodb {
    Map<String, Object> executeDecisionRules(Map<String, Object> context);
}

// Service under test that orchestrates validation and decision logic
class ClaimDataStandardizationValidationService {
    private final DocumentStoreService_s3 documentStoreService;
    private final PolicyValidationService_dynamodb policyValidationService;
    private final RulesEngineService_dynamodb rulesEngineService;

    ClaimDataStandardizationValidationService(
            DocumentStoreService_s3 documentStoreService,
            PolicyValidationService_dynamodb policyValidationService,
            RulesEngineService_dynamodb rulesEngineService) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
    }

    Map<String, Object> evaluateDecision(String claimId, Map<String, Object> payload) {
        Map<String, Object> claimPayload = (Map<String, Object>) payload.get("payload");
        String address = (String) claimPayload.get("address");

        Map<String, Object> existingPolicy = policyValidationService.fetchPolicyItemByAddress(address);
        boolean addressMatches = address.equals(existingPolicy.get("address"));
        boolean insuredMatches = claimPayload.get("insured").equals(existingPolicy.get("insured"));

        Map<String, Object> decisionContext = Map.of(
                "addressMatches", addressMatches,
                "insuredMatches", insuredMatches
        );

        Map<String, Object> decision = rulesEngineService.executeDecisionRules(decisionContext);
        decision.put("addressMatches", addressMatches);
        decision.put("insuredMatches", insuredMatches);
        return decision;
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private DocumentStoreService_s3 documentStoreService;

    @Mock
    private PolicyValidationService_dynamodb policyValidationService;

    @Mock
    private RulesEngineService_dynamodb rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationValidationService service;

    @BeforeEach
    void setUp() {
        // Mocks are initialized automatically by MockitoExtension
    }

    @Test
    void address_matches_but_insured_differs() {
        // Arrange
        String claimId = "claim-98765";
        Map<String, Object> claimEntityPayload = Map.of(
                "id", claimId,
                "payload", Map.of(
                        "address", "456 Elm Street",
                        "insured", "Eleanor Rigby"
                )
        );

        Map<String, Object> existingPolicyRecord = Map.of(
                "address", "456 Elm Street",
                "insured", "Frank Underwood"
        );

        when(policyValidationService.fetchPolicyItemByAddress("456 Elm Street")).thenReturn(existingPolicyRecord);
        when(rulesEngineService.executeDecisionRules(anyMap())).thenReturn(Map.of(
                "status", "FLAGGED_FOR_REVIEW",
                "code", "INSURED_MISMATCH_DETECTED"
        ));

        // Act
        Map<String, Object> decisionResult = service.evaluateDecision(claimId, claimEntityPayload);

        // Assert
        assertNotNull(decisionResult, "Decision result should not be null");
        assertEquals("FLAGGED_FOR_REVIEW", decisionResult.get("status"), "Status should indicate review");
        assertEquals("INSURED_MISMATCH_DETECTED", decisionResult.get("code"), "Code should reflect insured mismatch");
        assertTrue((Boolean) decisionResult.get("addressMatches"), "Address should be marked as matched");
        assertFalse((Boolean) decisionResult.get("insuredMatches"), "Insured should be marked as differing");

        // Verify external I/O contracts were invoked exactly once
        verify(policyValidationService, times(1)).fetchPolicyItemByAddress("456 Elm Street");
        verify(rulesEngineService, times(1)).executeDecisionRules(anyMap());
        verifyNoInteractions(documentStoreService);
    }
}
