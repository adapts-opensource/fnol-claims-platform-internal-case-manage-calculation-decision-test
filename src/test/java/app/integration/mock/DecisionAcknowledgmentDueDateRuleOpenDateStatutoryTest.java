package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDecisionCalculationMockTest {

    @Mock
    private StatutoryWindowCalculator statutoryWindowCalculator;

    @Mock
    private DeadlineTaskRepository deadlineTaskRepository;

    @InjectMocks
    private ClaimDecisionCalculationService claimDecisionCalculationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock injection and lifecycle
    }

    @Test
    void decision_acknowledgment_due_date_rule_open_date_statutory_window_expected_outcome_deadline_task_created() {
        // Arrange
        LocalDate claimOpenDate = LocalDate.of(2024, 1, 15);
        int statutoryWindowDays = 30;
        LocalDate expectedDueDate = claimOpenDate.plusDays(statutoryWindowDays);

        Map<String, Object> payload = Map.of(
                "claimId", "CLM-78901",
                "openDate", claimOpenDate.toString(),
                "decisionType", DecisionType.ACKNOWLEDGMENT,
                "statutoryWindowDays", statutoryWindowDays
        );

        when(statutoryWindowCalculator.calculateDueDate(claimOpenDate, statutoryWindowDays))
                .thenReturn(expectedDueDate);

        // Act
        claimDecisionCalculationService.processClaimDecision(payload);

        // Assert
        ArgumentCaptor<DeadlineTask> taskCaptor = ArgumentCaptor.forClass(DeadlineTask.class);
        verify(deadlineTaskRepository, times(1)).save(taskCaptor.capture());

        DeadlineTask capturedTask = taskCaptor.getValue();
        assertEquals("CLM-78901", capturedTask.getClaimId());
        assertEquals(DecisionType.ACKNOWLEDGMENT, capturedTask.getTaskType());
        assertEquals(expectedDueDate, capturedTask.getDueDate());
        assertTrue(capturedTask.isActive());
        verify(statutoryWindowCalculator, times(1)).calculateDueDate(claimOpenDate, statutoryWindowDays);
    }

    // Domain interfaces to support isolated mocking
    interface StatutoryWindowCalculator {
        LocalDate calculateDueDate(LocalDate openDate, int days);
    }

    interface DeadlineTaskRepository {
        DeadlineTask save(DeadlineTask task);
    }

    static class DeadlineTask {
        private final String claimId;
        private final DecisionType taskType;
        private final LocalDate dueDate;
        private final boolean active;

        DeadlineTask(String claimId, DecisionType taskType, LocalDate dueDate) {
            this.claimId = claimId;
            this.taskType = taskType;
            this.dueDate = dueDate;
            this.active = true;
        }

        public String getClaimId() { return claimId; }
        public DecisionType getTaskType() { return taskType; }
        public LocalDate getDueDate() { return dueDate; }
        public boolean isActive() { return active; }
    }

    // Service under test
    class ClaimDecisionCalculationService {
        private final StatutoryWindowCalculator statutoryWindowCalculator;
        private final DeadlineTaskRepository deadlineTaskRepository;

        ClaimDecisionCalculationService(StatutoryWindowCalculator statutoryWindowCalculator,
                                        DeadlineTaskRepository deadlineTaskRepository) {
            this.statutoryWindowCalculator = statutoryWindowCalculator;
            this.deadlineTaskRepository = deadlineTaskRepository;
        }

        void processClaimDecision(Map<String, Object> payload) {
            String claimId = (String) payload.get("claimId");
            LocalDate openDate = LocalDate.parse((String) payload.get("openDate"));
            DecisionType decisionType = (DecisionType) payload.get("decisionType");
            int statutoryDays = (int) payload.get("statutoryWindowDays");

            LocalDate dueDate = statutoryWindowCalculator.calculateDueDate(openDate, statutoryDays);
            DeadlineTask task = new DeadlineTask(claimId, decisionType, dueDate);
            deadlineTaskRepository.save(task);
        }
    }
}
