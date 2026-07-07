package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:validation:decision feature.
 * Verifies that an effective date in the past triggers a validation failure decision.
 */
public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimDataStandardizationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        decisionService = new ClaimDataStandardizationDecisionService(documentStoreService, policyValidationService, rulesEngineService);
    }

    @Test
    void effective_date_in_past() {
        String claimId = "CLM-2023-001";
        LocalDate pastEffectiveDate = LocalDate.now().minusDays(10);
        Map<String, Object> claimPayload = Map.of(
            "id", claimId,
            "effectiveDate", pastEffectiveDate.toString(),
            "policyNumber", "POL-98765"
        );

        when(documentStoreService.retrieveObject(anyString(), anyString())).thenReturn(claimPayload);

        ValidationDecision decision = decisionService.evaluateDecision(claimPayload);

        assertFalse(decision.isValid(), "Decision should be invalid when effective date is in the past");
        assertEquals("EFFECTIVE_DATE_IN_PAST", decision.getReasonCode(), "Reason code should match expected validation rule");
        assertEquals(ValidationDecision.Severity.HIGH, decision.getSeverity(), "Severity should be high for past effective dates");

        verify(documentStoreService, times(1)).retrieveObject(eq("DocumentStoreService-bucket"), eq("DocumentStoreService/" + claimId + ".json"));
        verifyNoInteractions(policyValidationService, rulesEngineService);
    }
}

// Supporting interfaces and model classes for compilation
interface DocumentStoreService {
    Map<String, Object> retrieveObject(String bucketName, String objectKey);
}

interface PolicyValidationService {
    Map<String, Object> getItem(String tableName, String partitionKey);
}

interface RulesEngineService {
    Map<String, Object> getItem(String tableName, String partitionKey);
}

record ValidationDecision(boolean isValid, String reasonCode, Severity severity) {
    enum Severity { LOW, MEDIUM, HIGH }
}

class ClaimDataStandardizationDecisionService {
    private final DocumentStoreService documentStoreService;
    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;

    ClaimDataStandardizationDecisionService(DocumentStoreService documentStoreService, PolicyValidationService policyValidationService, RulesEngineService rulesEngineService) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
    }

    ValidationDecision evaluateDecision(Map<String, Object> payload) {
        String effectiveDateStr = (String) payload.get("effectiveDate");
        if (effectiveDateStr != null) {
            LocalDate effectiveDate = LocalDate.parse(effectiveDateStr);
            if (effectiveDate.isBefore(LocalDate.now())) {
                return new ValidationDecision(false, "EFFECTIVE_DATE_IN_PAST", ValidationDecision.Severity.HIGH);
            }
        }
        return new ValidationDecision(true, "VALID", ValidationDecision.Severity.LOW);
    }
}
