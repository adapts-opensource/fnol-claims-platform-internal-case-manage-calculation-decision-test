package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DolValidationCorrectlyIdentifiesPeriodRestrictions {

    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private RulesEngineService rulesEngineService;
    private DolValidationService dolValidationService;

    @BeforeEach
    void setUp() {
        dolValidationService = new DolValidationService(documentStoreService, policyValidationService, rulesEngineService);
    }

    @Test
    void dol_validation_correctly_identifies_period_restrictions() {
        String claimId = "claim-123";
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "dolPeriod", "2023-01-01/2023-12-31",
                "restrictions", "full_time, no_overtime"
        );

        when(documentStoreService.retrieveObject(anyString(), anyString())).thenReturn(payload);
        when(policyValidationService.getRules(anyString())).thenReturn(Map.of("dol_enabled", true));
        when(rulesEngineService.evaluate(anyString())).thenReturn(Map.of("decision", "APPROVED", "flags", "period_restriction_checked"));

        ValidationResult result = dolValidationService.validateClaimData(claimId, payload);

        assertNotNull(result);
        assertTrue(result.isPeriodRestrictionIdentified());
        assertEquals("APPROVED", result.getDecision());
        verify(documentStoreService, times(1)).retrieveObject(anyString(), anyString());
    }
}

interface DocumentStoreService {
    Map<String, Object> retrieveObject(String bucketName, String objectKey);
}

interface PolicyValidationService {
    Map<String, Object> getRules(String tableName);
}

interface RulesEngineService {
    Map<String, Object> evaluate(String tableName);
}

class DolValidationService {
    private final DocumentStoreService documentStoreService;
    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;

    DolValidationService(DocumentStoreService documentStoreService, PolicyValidationService policyValidationService, RulesEngineService rulesEngineService) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
    }

    ValidationResult validateClaimData(String id, Map<String, Object> payload) {
        Map<String, Object> storedPayload = documentStoreService.retrieveObject("DocumentStoreService-bucket", "DocumentStoreService/" + id + ".json");
        Map<String, Object> rules = policyValidationService.getRules("PolicyValidationService_table");
        Map<String, Object> decision = rulesEngineService.evaluate("RulesEngineService_table");
        
        boolean periodRestrictionIdentified = storedPayload.containsKey("dolPeriod") && storedPayload.containsKey("restrictions");
        return new ValidationResult(decision.get("decision").toString(), periodRestrictionIdentified);
    }
}

class ValidationResult {
    private final String decision;
    private final boolean periodRestrictionIdentified;

    ValidationResult(String decision, boolean periodRestrictionIdentified) {
        this.decision = decision;
        this.periodRestrictionIdentified = periodRestrictionIdentified;
    }

    String getDecision() {
        return decision;
    }

    boolean isPeriodRestrictionIdentified() {
        return periodRestrictionIdentified;
    }
}
