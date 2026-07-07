package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Map;

// External I/O contract interfaces (mocked)
interface DocumentStoreService {
    Map<String, Object> retrieveObject(String bucketName, String objectKey);
}

interface PolicyValidationService {
    boolean validateInput(Map<String, Object> payload);
}

interface RulesEngineService {
    String evaluate(Map<String, Object> payload);
}

// Domain record for decision outcome
record DecisionResult(String decisionStatus, boolean completenessCheckPassed) {}

// Service under test
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

    DecisionResult evaluateDecision(String claimId, Map<String, Object> payload) {
        boolean isComplete = policyValidationService.validateInput(payload);
        String status = isComplete ? rulesEngineService.evaluate(payload) : DecisionStatus.REJECTED.name();
        return new DecisionResult(status, isComplete);
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimDataValidationDecisionCompletenessCheckTest {

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
            documentStoreService, policyValidationService, rulesEngineService
        );
    }

    @Test
    void decision_completeness_check() {
        // arrange: simulate complete claim payload meeting validation rules
        String claimId = "CLM-STD-001";
        Map<String, Object> completePayload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "claimType", "AUTO",
                "incidentDate", "2023-10-01T12:00:00Z",
                "policyNumber", "POL-9876",
                "lossDetails", Map.of("damageType", "COLLISION", "amount", 5000)
            )
        );

        // mock external I/O contracts
        when(documentStoreService.retrieveObject(anyString(), anyString())).thenReturn(completePayload);
        when(policyValidationService.validateInput(completePayload)).thenReturn(true);
        when(rulesEngineService.evaluate(completePayload)).thenReturn(DecisionStatus.APPROVED.name());

        // act: invoke decision logic
        var result = decisionService.evaluateDecision(claimId, completePayload);

        // assert: verify completeness check passes and decision is valid
        assertNotNull(result, "Decision result should not be null");
        assertEquals(DecisionStatus.APPROVED.name(), result.decisionStatus(),
            "Decision status should be APPROVED for complete data");
        assertTrue(result.completenessCheckPassed(), "Completeness check should pass");
        verify(documentStoreService, times(1)).retrieveObject(anyString(), anyString());
        verify(policyValidationService, times(1)).validateInput(completePayload);
        verify(rulesEngineService, times(1)).evaluate(completePayload);
    }
}
