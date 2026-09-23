package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class RuleConfigurationValidationMockTest {

    @Mock
    private ClaimRuleValidationService mockValidationService;

    @Mock
    private AuditTrailService mockAuditService;

    private RuleConfigurationManager ruleManager;

    @BeforeEach
    void setUp() {
        ruleManager = new RuleConfigurationManager(mockValidationService, mockAuditService);
    }

    @Test
    void purpose_validate_version_and_activate_rule_configurations_ensure_backward_compatibility_and_auditability() {
        // Given: Legacy claim data payload and a new rule configuration version
        Map<String, Object> legacyPayload = Map.of("claimId", "CLM-001", "standardVersion", "1.0", "pii", false);
        Map<String, Object> newRuleConfig = Map.of("ruleId", "STD-RULE-001", "version", "2.0", "active", true);

        // When: Validating, versioning, and activating the rule configuration
        when(mockValidationService.validateConfig(anyMap())).thenReturn(true);
        when(mockValidationService.resolveNextVersion("1.0")).thenReturn("2.0");
        when(mockValidationService.activateRule(anyString(), anyString())).thenReturn(true);
        doNothing().when(mockAuditService).recordVersionChange(anyString(), anyString(), anyString());

        boolean activationResult = ruleManager.validateVersionAndActivate(newRuleConfig, legacyPayload);

        // Then: Verify validation passed, version was incremented, activation succeeded, and audit trail is recorded
        assertTrue(activationResult, "Rule configuration should be successfully activated");
        verify(mockValidationService).validateConfig(newRuleConfig);
        verify(mockValidationService).resolveNextVersion("1.0");
        verify(mockValidationService).activateRule("STD-RULE-001", "2.0");
        verify(mockAuditService).recordVersionChange("STD-RULE-001", "1.0", "2.0");
        verifyNoMoreInteractions(mockValidationService, mockAuditService);
    }
}
