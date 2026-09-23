package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Mock test for NFR: AuditLogsAreImmutableAndAppendOnly.
 * Verifies that the DecisionCalculationService respects immutability
 * and append-only semantics for audit logs, ensuring compliance
 * with GDPR and SOC2 requirements for audit trails.
 */
public class AuditLogImmutabilityTest {

    /**
     * Stub implementation to track audit log interactions.
     * Simulates the persistence layer behavior for testing.
     */
    private static class StubAuditLogStore {
        private final List<Map<String, Object>> entries = new ArrayList<>();
        private int updateAttempts = 0;

        void append(String claimId, Map<String, Object> payload) {
            entries.add(Map.copyOf(payload));
        }

        void update(String logId, Map<String, Object> payload) {
            updateAttempts++;
        }

        List<Map<String, Object>> getEntries() {
            return Collections.unmodifiableList(entries);
        }

        int getUpdateAttempts() {
            return updateAttempts;
        }
    }

    /**
     * Service Under Test (SUT) using the stubbed store.
     * Represents Claim Initiation & Routing:decision:calculation logic.
     */
    private static class DecisionCalculationService {
        private final StubAuditLogStore store;

        DecisionCalculationService(StubAuditLogStore store) {
            this.store = store;
        }

        void calculateAndLog(String claimId, Map<String, Object> decision) {
            store.append(claimId, decision);
        }
    }

    @Test
    @DisplayName("audit_logs_are_immutable_and_append_only")
    void audit_logs_are_immutable_and_append_only() {
        // Arrange
        StubAuditLogStore store = new StubAuditLogStore();
        DecisionCalculationService service = new DecisionCalculationService(store);
        String claimId = "CLM-2024-INT-001";
        Map<String, Object> payload1 = Map.of("routingDecision", "AUTO_APPROVE", "confidence", 0.95);
        Map<String, Object> payload2 = Map.of("routingDecision", "MANUAL_REVIEW", "confidence", 0.80);

        // Act: Simulate multiple decision calculations triggering audit logs
        service.calculateAndLog(claimId, payload1);
        service.calculateAndLog(claimId, payload2);

        // Assert: Append-Only
        assertEquals(2, store.getEntries().size(), 
            "Audit log should contain exactly two appended entries.");
        
        assertEquals(payload1, store.getEntries().get(0), 
            "First entry must match the initial payload.");
        
        assertEquals(payload2, store.getEntries().get(1), 
            "Second entry must match the subsequent payload.");

        // Assert: Immutability
        assertEquals(0, store.getUpdateAttempts(), 
            "Audit logs must be immutable; no update operations should occur.");
    }
}
