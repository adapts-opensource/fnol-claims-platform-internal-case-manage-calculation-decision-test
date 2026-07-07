package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementTrackingValidationMockTest {

    @Mock
    private Logger auditLogger;
    @Mock
    private TaskDispatcher taskDispatcher;
    @Mock
    private ClaimRepository claimRepository;

    private InsuredEngagementValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new InsuredEngagementValidationService(claimRepository, taskDispatcher, auditLogger);
    }

    @Test
    void validate_date_of_loss_outside_policy_period_routed_to_coverage_review() {
        // Arrange
        String policyNumber = "POL-99999";
        LocalDate lossDate = LocalDate.of(2024, 1, 1);
        LocalDate policyEffectiveDate = LocalDate.of(2024, 2, 1);
        String productForm = "HO3";

        Map<String, Object> payload = Map.of(
                "policy_number", policyNumber,
                "loss_date", lossDate.toString(),
                "policy_effective_date", policyEffectiveDate.toString(),
                "product_form", productForm
        );

        ClaimCaptureResult expectedCaptureResult = new ClaimCaptureResult("COVERAGE_TRIAGE", false);
        when(claimRepository.capture(anyMap())).thenReturn(expectedCaptureResult);
        doNothing().when(taskDispatcher).dispatch(anyString(), anyString());

        // Act
        ClaimCaptureResult result = validationService.processTransformation(payload);

        // Assert
        assertNotNull(result, "Claim result should not be null");
        assertEquals("COVERAGE_TRIAGE", result.status(), "Claim should be captured with Coverage Triage status");
        assertFalse(result.intakeRejected(), "Intake should not be rejected when loss date is outside policy period");
        verify(taskDispatcher).dispatch(eq("COVERAGE_REVIEW"), eq(policyNumber), "Coverage Review task should be generated");
        verify(auditLogger).log(eq(Level.WARNING), eq("Date mismatch: loss date {0} precedes policy effective date {1}"), eq(lossDate), eq(policyEffectiveDate), "Warning should be logged for date mismatch");
    }

    // Minimal domain models for test isolation
    record ClaimCaptureResult(String status, boolean intakeRejected) {}

    static class InsuredEngagementValidationService {
        private final ClaimRepository claimRepository;
        private final TaskDispatcher taskDispatcher;
        private final Logger auditLogger;

        InsuredEngagementValidationService(ClaimRepository claimRepository, TaskDispatcher taskDispatcher, Logger auditLogger) {
            this.claimRepository = claimRepository;
            this.taskDispatcher = taskDispatcher;
            this.auditLogger = auditLogger;
        }

        ClaimCaptureResult processTransformation(Map<String, Object> payload) {
            String policyNumber = (String) payload.get("policy_number");
            LocalDate lossDate = LocalDate.parse((String) payload.get("loss_date"));
            LocalDate policyEffectiveDate = LocalDate.parse((String) payload.get("policy_effective_date"));

            if (lossDate.isBefore(policyEffectiveDate)) {
                auditLogger.log(Level.WARNING, "Date mismatch: loss date {0} precedes policy effective date {1}", lossDate, policyEffectiveDate);
                taskDispatcher.dispatch("COVERAGE_REVIEW", policyNumber);
                return claimRepository.capture(payload);
            }
            throw new IllegalArgumentException("Loss date must be on or after policy effective date");
        }
    }

    interface ClaimRepository {
        ClaimCaptureResult capture(Map<String, Object> payload);
    }

    interface TaskDispatcher {
        void dispatch(String taskType, String entityId);
    }
}
