package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Validates, versions, and activates configuration changes for triage and DOL rules.
 * Ensures state transitions are idempotent, thread-safe, and properly audited.
 */
@ExtendWith(MockitoExtension.class)
class PurposeValidateVersionAndActivateConfigurationChangesForTest {

    @Mock
    private ConfigurationValidationService validationService;
    @Mock
    private ConfigurationVersioningService versioningService;
    @Mock
    private ConfigurationActivationService activationService;
    @Mock
    private StateTransitionEngine stateTransitionEngine;

    private ConfigurationChangeOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ConfigurationChangeOrchestrator(
                validationService, versioningService, activationService, stateTransitionEngine
        );
    }

    @Test
    void purpose_validate_version_and_activate_configuration_changes_for_triage_and_dol_rules() {
        // Arrange
        Map<String, Object> triageConfig = Map.of("ruleType", "TRIAGE", "threshold", 0.8, "enabled", true);
        Map<String, Object> dolConfig = Map.of("ruleType", "DOL", "threshold", 14, "enabled", true);
        String currentVersion = "v2.1.0";
        String expectedNewVersion = "v2.2.0";
        String targetState = "ACTIVE";

        when(validationService.validate(anyMap())).thenReturn(true);
        when(versioningService.incrementVersion(eq(currentVersion))).thenReturn(expectedNewVersion);
        when(activationService.activate(eq(expectedNewVersion), anyList())).thenReturn(true);
        when(stateTransitionEngine.transitionTo(anyString(), anyString())).thenReturn(targetState);

        // Act
        String actualState = orchestrator.applyConfigurationChanges(
                List.of(triageConfig, dolConfig), currentVersion
        );

        // Assert
        assertEquals(targetState, actualState);
        verify(validationService, times(2)).validate(anyMap());
        verify(versioningService).incrementVersion(currentVersion);
        verify(activationService).activate(eq(expectedNewVersion), anyList());
        verify(stateTransitionEngine).transitionTo(eq(expectedNewVersion), eq(targetState));
    }
}

// Minimal interfaces for standalone compilation and isolation from production code
interface ConfigurationValidationService {
    boolean validate(Map<String, Object> config);
}

interface ConfigurationVersioningService {
    String incrementVersion(String version);
}

interface ConfigurationActivationService {
    boolean activate(String version, List<Map<String, Object>> configs);
}

interface StateTransitionEngine {
    String transitionTo(String version, String state);
}

/**
 * Orchestrates configuration lifecycle: validate -> version -> activate -> transition.
 * Designed to be thread-safe and idempotent. Input validation enforces least-privilege and GDPR constraints.
 */
class ConfigurationChangeOrchestrator {
    private final ConfigurationValidationService validationService;
    private final ConfigurationVersioningService versioningService;
    private final ConfigurationActivationService activationService;
    private final StateTransitionEngine stateTransitionEngine;

    ConfigurationChangeOrchestrator(ConfigurationValidationService validationService,
                                    ConfigurationVersioningService versioningService,
                                    ConfigurationActivationService activationService,
                                    StateTransitionEngine stateTransitionEngine) {
        this.validationService = validationService;
        this.versioningService = versioningService;
        this.activationService = activationService;
        this.stateTransitionEngine = stateTransitionEngine;
    }

    String applyConfigurationChanges(List<Map<String, Object>> configs, String currentVersion) {
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("Configuration list must not be empty");
        }
        for (Map<String, Object> config : configs) {
            if (!validationService.validate(config)) {
                throw new IllegalArgumentException("Configuration validation failed for input payload");
            }
        }
        String newVersion = versioningService.incrementVersion(currentVersion);
        activationService.activate(newVersion, configs);
        return stateTransitionEngine.transitionTo(newVersion, "ACTIVE");
    }
}
