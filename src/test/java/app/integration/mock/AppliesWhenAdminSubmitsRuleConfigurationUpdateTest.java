package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationEnrichmentValidationTest {

    @Mock
    private RuleConfigurationUpdateHandler ruleUpdateHandler;

    @Mock
    private ClaimDataValidationEngine validationEngine;

    private ClaimDataStandardizationEnrichmentValidation service;

    @BeforeEach
    void setUp() {
        service = new ClaimDataStandardizationEnrichmentValidation(ruleUpdateHandler, validationEngine);
    }

    @Test
    void applies_when_admin_submits_rule_configuration_update() {
        String adminId = "admin-sub-001";
        Map<String, Object> ruleConfigUpdate = new HashMap<>();
        ruleConfigUpdate.put("ruleId", "STD_RULE_001");
        ruleConfigUpdate.put("action", "UPDATE");
        ruleConfigUpdate.put("payload", Map.of("enrichment", "ENABLED", "validation", "STRICT"));

        when(ruleUpdateHandler.validateAdminSubmission(adminId)).thenReturn(true);
        when(validationEngine.enrichAndValidate(ruleConfigUpdate)).thenReturn(true);

        boolean result = service.processRuleConfigurationUpdate(adminId, ruleConfigUpdate);

        assertTrue(result, "Should apply enrichment and validation when admin submits rule configuration update");
        verify(ruleUpdateHandler).validateAdminSubmission(adminId);
        verify(validationEngine).enrichAndValidate(ruleConfigUpdate);
    }
}

class ClaimDataStandardizationEnrichmentValidation {
    private final RuleConfigurationUpdateHandler ruleUpdateHandler;
    private final ClaimDataValidationEngine validationEngine;

    ClaimDataStandardizationEnrichmentValidation(RuleConfigurationUpdateHandler ruleUpdateHandler, ClaimDataValidationEngine validationEngine) {
        this.ruleUpdateHandler = ruleUpdateHandler;
        this.validationEngine = validationEngine;
    }

    boolean processRuleConfigurationUpdate(String adminId, Map<String, Object> ruleConfigUpdate) {
        if (!ruleUpdateHandler.validateAdminSubmission(adminId)) {
            return false;
        }
        return validationEngine.enrichAndValidate(ruleConfigUpdate);
    }
}

interface RuleConfigurationUpdateHandler {
    boolean validateAdminSubmission(String adminId);
}

interface ClaimDataValidationEngine {
    boolean enrichAndValidate(Map<String, Object> payload);
}
