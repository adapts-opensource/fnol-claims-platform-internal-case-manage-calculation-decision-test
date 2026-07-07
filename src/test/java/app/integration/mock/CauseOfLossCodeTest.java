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

// Minimal interfaces to mock external I/O contracts (S3, DynamoDB)
interface DocumentStoreService {
    Map<String, Object> readObject(String bucketName, String objectKeyPattern);
}
interface PolicyValidationService {
    Map<String, String> getValidationRules(String entityId);
}
interface RulesEngineService {
    Map<String, Object> evaluate(String entityId, Map<String, Object> payload);
}

class ClaimStandardizationValidationService {
    private final DocumentStoreService documentStoreService;
    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;

    ClaimStandardizationValidationService(DocumentStoreService documentStoreService,
                                          PolicyValidationService policyValidationService,
                                          RulesEngineService rulesEngineService) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
    }

    Map<String, Object> validateDecision(String entityId, Map<String, Object> payload) {
        Map<String, Object> storedData = documentStoreService.readObject("DocumentStoreService-bucket", "DocumentStoreService/" + entityId + ".json");
        Map<String, String> rules = policyValidationService.getValidationRules(entityId);
        Map<String, Object> decision = rulesEngineService.evaluate(entityId, payload);
        decision.put("cause_of_loss_code", payload.get("cause_of_loss_code"));
        return decision;
    }
}

@ExtendWith(MockitoExtension.class)
public class CauseOfLossCodeValidationDecisionTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimStandardizationValidationService validationService;

    @BeforeEach
    void setUp() {
        // NFR: Input validation and thread safety are enforced by service contracts and mock isolation
    }

    @Test
    void cause_of_loss_code() {
        // Arrange
        String claimId = "CLM-789-COL";
        String bucketName = "DocumentStoreService-bucket";
        String objectKeyPattern = "DocumentStoreService/" + claimId + ".json";
        Map<String, Object> payload = Map.of("cause_of_loss_code", "COLLISION", "claim_id", claimId);

        when(documentStoreService.readObject(bucketName, objectKeyPattern)).thenReturn(payload);
        when(policyValidationService.getValidationRules(claimId)).thenReturn(Map.of("cause_of_loss_code", "VALID"));
        when(rulesEngineService.evaluate(claimId, payload)).thenReturn(Map.of("decision", "APPROVED"));

        // Act
        Map<String, Object> result = validationService.validateDecision(claimId, payload);

        // Assert
        assertNotNull(result, "Validation decision must not be null");
        assertEquals("APPROVED", result.get("decision"), "Decision should be APPROVED for valid cause of loss");
        assertEquals("COLLISION", result.get("cause_of_loss_code"), "Cause of loss code must be preserved in standardization");
        verify(documentStoreService).readObject(bucketName, objectKeyPattern);
        verify(policyValidationService).getValidationRules(claimId);
        verify(rulesEngineService).evaluate(claimId, payload);
    }
}
