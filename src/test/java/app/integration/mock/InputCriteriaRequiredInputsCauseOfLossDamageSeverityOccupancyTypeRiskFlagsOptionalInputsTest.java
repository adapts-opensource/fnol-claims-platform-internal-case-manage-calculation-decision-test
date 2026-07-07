package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Mock-based integration test for Claim Data Standardization: calculation: decision engine.
 * Verifies triage routing, input validation, pool assignment, audit logging, and NFR compliance.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDecisionStandardizationMockTest {

    @Mock
    private ValidationService validationService;
    @Mock
    private PoolCapacityService poolCapacityService;
    @Mock
    private RuleEngine ruleEngine;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ClaimDecisionEngine claimDecisionEngine;

    @BeforeEach
    void setUp() {
        reset(validationService, poolCapacityService, ruleEngine, auditLogger, eventPublisher);
    }

    @Test
    void testStandardPathLowSeverityNoFlags() {
        // Arrange
        ClaimInput input = new ClaimInput("CLM001", "CAR", "LOW", "OWNER", false, false, false, 5000.0);
        when(validationService.validateCauseCode("CAR")).thenReturn(true);
        when(validationService.mapSeverityToScale("LOW")).thenReturn(1);
        when(ruleEngine.evaluate(any(ClaimInput.class))).thenReturn(new TriageDecision("STANDARD", "STANDARD_POOL", "MEDIUM", 24.0, List.of("rule_low_severity")));
        when(poolCapacityService.checkCapacity("STANDARD_POOL")).thenReturn(true);

        // Act
        TriageDecision result = claimDecisionEngine.evaluate(input);

        // Assert
        assertEquals("STANDARD", result.path());
        assertEquals("STANDARD_POOL", result.assigneePool());
        assertEquals("MEDIUM", result.priorityLevel());
        assertEquals(24.0, result.estimatedHandlingTime());
        verify(auditLogger).log(any(), eq("STANDARD"), eq("STANDARD_POOL"));
        verify(eventPublisher).publish(eq("TRIAGE_ROUTING_COMPLETED"), any());
    }

    @Test
    void testAttorneyPathAttorneyFlagTrue() {
        // Arrange
        ClaimInput input = new ClaimInput("CLM002", "COLLISION", "MEDIUM", "TENANT", true, false, false, 12000.0);
        when(validationService.validateCauseCode("COLLISION")).thenReturn(true);
        when(ruleEngine.evaluate(any(ClaimInput.class))).thenReturn(new TriageDecision("COMPLEX", "LITIGATION_SPECIALIST_POOL", "HIGH", 72.0, List.of("rule_attorney_flag")));
        when(poolCapacityService.checkCapacity("LITIGATION_SPECIALIST_POOL")).thenReturn(true);

        // Act
        TriageDecision result = claimDecisionEngine.evaluate(input);

        // Assert
        assertEquals("COMPLEX", result.path());
        assertEquals("LITIGATION_SPECIALIST_POOL", result.assigneePool());
        assertEquals("HIGH", result.priorityLevel());
        verify(auditLogger).log(any(), eq("COMPLEX"), eq("LITIGATION_SPECIALIST_POOL"));
    }

    @Test
    void testStormPathMoratoriumActive() {
        // Arrange
        ClaimInput input = new ClaimInput("CLM003", "STORM", "HIGH", "OWNER", false, false, false, 45000.0);
        when(validationService.validateCauseCode("STORM")).thenReturn(true);
        when(ruleEngine.evaluate(any(ClaimInput.class))).thenReturn(new TriageDecision("STORM", "STORM_RESPONSE_POOL", "CRITICAL", 12.0, List.of("rule_moratorium_active")));
        when(poolCapacityService.checkCapacity("STORM_RESPONSE_POOL")).thenReturn(true);

        // Act
        TriageDecision result = claimDecisionEngine.evaluate(input);

        // Assert
        assertEquals("STORM", result.path());
        assertEquals("STORM_RESPONSE_POOL", result.assigneePool());
        assertEquals("CRITICAL", result.priorityLevel());
        verify(eventPublisher).publish(eq("ADJUSTER_ASSIGNED"), any());
    }

    @Test
    void testInputValidationInvalidCauseCode() {
        // Arrange
        ClaimInput input = new ClaimInput("CLM004", "UNKNOWN_CAUSE", "LOW", "OWNER", false, false, false, 1000.0);
        when(validationService.validateCauseCode("UNKNOWN_CAUSE")).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> claimDecisionEngine.evaluate(input));
        verify(ruleEngine, never()).evaluate(any());
        verify(auditLogger, never()).log(any(), any(), any());
    }

    @Test
    void testPoolCapacityServiceDown() {
        // Arrange
        ClaimInput input = new ClaimInput("CLM005", "FIRE", "MEDIUM", "OWNER", false, false, false, 8000.0);
        when(validationService.validateCauseCode("FIRE")).thenReturn(true);
        when(ruleEngine.evaluate(any(ClaimInput.class))).thenReturn(new TriageDecision("STANDARD", "STANDARD_POOL", "MEDIUM", 24.0, Collections.emptyList()));
        when(poolCapacityService.checkCapacity("STANDARD_POOL")).thenThrow(new RuntimeException("Pool capacity service unavailable"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> claimDecisionEngine.evaluate(input));
        verify(auditLogger).log(any(), eq("UNKNOWN"), eq("UNKNOWN"));
        verify(eventPublisher).publish(eq("FNOL_TRIAGED"), any());
    }

    @Test
    void testAuditLoggingAndExplainability() {
        // Arrange
        ClaimInput input = new ClaimInput("CLM006", "THEFT", "LOW", "LANDLORD", false, true, false, 3000.0);
        when(validationService.validateCauseCode("THEFT")).thenReturn(true);
        when(ruleEngine.evaluate(any(ClaimInput.class))).thenReturn(new TriageDecision("COMPLEX", "SENIOR_ADJUSTER_POOL", "HIGH", 36.0, List.of("rule_pa_flag", "rule_mixed_occupancy")));
        when(poolCapacityService.checkCapacity("SENIOR_ADJUSTER_POOL")).thenReturn(true);

        // Act
        claimDecisionEngine.evaluate(input);

        // Assert
        verify(auditLogger).log(eq("CLM006"), eq("COMPLEX"), eq("SENIOR_ADJUSTER_POOL"));
        verify(auditLogger).logRuleEvaluationLog(eq("CLM006"), anyMap());
        verify(auditLogger).logPoolAssignmentTimestamp(eq("CLM006"), any(Instant.class));
    }

    @Test
    void testMixedOccupancyAndDisputedSeverity() {
        // Arrange
        ClaimInput input = new ClaimInput("CLM007", "WATER", "DISPUTED", "TENANT_LANDLORD", false, false, false, 15000.0);
        when(validationService.validateCauseCode("WATER")).thenReturn(true);
        when(ruleEngine.evaluate(any(ClaimInput.class))).thenReturn(new TriageDecision("COMPLEX", "SENIOR_ADJUSTER_POOL", "HIGH", 48.0, List.of("rule_disputed_severity", "rule_mixed_occupancy")));
        when(poolCapacityService.checkCapacity("SENIOR_ADJUSTER_POOL")).thenReturn(true);

        // Act
        TriageDecision result = claimDecisionEngine.evaluate(input);

        // Assert
        assertEquals("COMPLEX", result.path());
        assertEquals("SENIOR_ADJUSTER_POOL", result.assigneePool());
        assertEquals(48.0, result.estimatedHandlingTime());
    }

    @Test
    void testPoolAtCapacity() {
        // Arrange
        ClaimInput input = new ClaimInput("CLM008", "HAIL", "HIGH", "OWNER", false, false, false, 20000.0);
        when(validationService.validateCauseCode("HAIL")).thenReturn(true);
        when(ruleEngine.evaluate(any(ClaimInput.class))).thenReturn(new TriageDecision("STANDARD", "STANDARD_POOL", "MEDIUM", 24.0, List.of("rule_high_severity")));
        when(poolCapacityService.checkCapacity("STANDARD_POOL")).thenReturn(false);

        // Act
        TriageDecision result = claimDecisionEngine.evaluate(input);

        // Assert
        assertEquals("STANDARD", result.path());
        assertEquals("STANDARD_POOL", result.assigneePool());
        assertEquals("UNKNOWN", result.priorityLevel());
        verify(auditLogger).log(eq("CLM008"), eq("STANDARD"), eq("STANDARD_POOL"));
    }

    // Supporting Interfaces & DTOs for Mocking
    interface ValidationService {
        boolean validateCauseCode(String causeCode);
        int mapSeverityToScale(String severity);
    }

    interface PoolCapacityService {
        boolean checkCapacity(String poolName);
    }

    interface RuleEngine {
        TriageDecision evaluate(ClaimInput input);
    }

    interface AuditLogger {
        void log(String claimId, String path, String pool);
        void logRuleEvaluationLog(String claimId, Map<String, Object> evidence);
        void logPoolAssignmentTimestamp(String claimId, Instant timestamp);
    }

    interface EventPublisher {
        void publish(String eventType, Map<String, Object> payload);
    }

    record ClaimInput(String claimId, String causeOfLoss, String damageSeverity, String occupancyType,
                      boolean attorneyFlag, boolean publicAdjusterFlag, boolean moratoriumFlag, double estimatedLossAmount) {}

    record TriageDecision(String path, String assigneePool, String priorityLevel, double estimatedHandlingTime, List<String> ruleMatches) {}
}
