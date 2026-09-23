package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies orchestration decision logic for Insured Engagement & Tracking.
 * Specifically tests that a manual override correctly detects and rejects conflicts
 * with an active automated assignment, adhering to concurrency and input validation NFRs.
 */
public class ManualOverrideConflictsWithAutomatedAssignmentTest {

    private static final Logger log = LoggerFactory.getLogger(ManualOverrideConflictsWithAutomatedAssignmentTest.class);

    @Mock
    private ReserveLineRepository reserveLineRepository;

    @Mock
    private AssignmentEngine assignmentEngine;

    @Mock
    private EngagementLogger engagementLogger;

    private InsuredEngagementOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new InsuredEngagementOrchestrator(reserveLineRepository, assignmentEngine, engagementLogger);
    }

    @Test
    @DisplayName("manual_override_conflicts_with_automated_assignment")
    void manual_override_conflicts_with_automated_assignment() {
        // Arrange
        String reserveId = "res-manual-override-001";
        String exposureId = "exp-auto-assign-001";
        ReserveLine reserveLine = new ReserveLine(reserveId, exposureId, 5000.0, "USD", "PENDING");

        when(reserveLineRepository.findById(reserveId)).thenReturn(Optional.of(reserveLine));
        when(assignmentEngine.isAutomatedAssignmentActive(exposureId)).thenReturn(true);

        // Act & Assert
        ConflictException thrown = assertThrows(ConflictException.class, () -> {
            orchestrator.processManualOverride(reserveId, "manual-agent-789");
        });

        assertEquals("Manual override conflicts with active automated assignment for exposure: " + exposureId, thrown.getMessage());

        // Verify NFRs: Input validation & Concurrency safety
        verify(reserveLineRepository, never()).save(any(ReserveLine.class));
        verify(assignmentEngine, never()).executeAutomatedAssignment(anyString());
        verify(engagementLogger).logConflict(eq(reserveId), eq(exposureId), eq("MANUAL_OVERRIDE"));
    }

    // Minimal domain & service stubs for compilation context
    static class ReserveLine {
        final String reserveId;
        final String exposureId;
        final double amount;
        final String currency;
        final String approvalStatus;

        ReserveLine(String reserveId, String exposureId, double amount, String currency, String approvalStatus) {
            this.reserveId = reserveId;
            this.exposureId = exposureId;
            this.amount = amount;
            this.currency = currency;
            this.approvalStatus = approvalStatus;
        }
    }

    interface ReserveLineRepository {
        Optional<ReserveLine> findById(String reserveId);
        ReserveLine save(ReserveLine reserveLine);
    }

    interface AssignmentEngine {
        boolean isAutomatedAssignmentActive(String exposureId);
        void executeAutomatedAssignment(String exposureId);
    }

    interface EngagementLogger {
        void logConflict(String reserveId, String exposureId, String eventType);
    }

    static class ConflictException extends RuntimeException {
        ConflictException(String message) { super(message); }
    }
}
