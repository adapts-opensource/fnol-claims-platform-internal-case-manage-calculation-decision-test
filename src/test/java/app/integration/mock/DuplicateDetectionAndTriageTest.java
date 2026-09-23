package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class DuplicateDetectionAndTriageTest {

    @Mock
    private ClaimsHistoryService claimsHistoryService;

    @Mock
    private TaskGeneratorService taskGeneratorService;

    @Mock
    private AuditLogService auditLogService;

    private DuplicateDetectionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DuplicateDetectionOrchestrator(claimsHistoryService, taskGeneratorService, auditLogService);
    }

    @Test
    void orchestrate_duplicate_detection_and_triage() {
        // Arrange
        String policyNumber = "POL-12345";
        String riskAddress = "123 Main St";
        LocalDate dateOfLoss = LocalDate.of(2023, 12, 1);
        String causeOfLoss = "wind";
        String existingClaimId = "CLM-98765";
        String reporter = "john.doe@example.com";

        Map<String, Object> existingClaimData = Map.of(
            "claimId", existingClaimId,
            "policyNumber", policyNumber,
            "riskAddress", riskAddress,
            "dateOfLoss", dateOfLoss,
            "causeOfLoss", causeOfLoss
        );

        when(claimsHistoryService.searchForDuplicates(policyNumber, riskAddress, dateOfLoss, causeOfLoss))
            .thenReturn(Optional.of(existingClaimData));

        Map<String, String> expectedTask = Map.of(
            "label", "Review Potential Duplicate Claim",
            "triageDimension", "fraud_indicators",
            "workflowState", "awaiting_duplicate_review"
        );

        when(taskGeneratorService.createTriageTask(anyMap(), anyString(), anyString()))
            .thenReturn(expectedTask);

        // Act
        Map<String, Object> result = orchestrator.orchestrateDuplicateDetectionAndTriage(
            policyNumber, riskAddress, dateOfLoss, causeOfLoss, existingClaimId, reporter
        );

        // Assert
        assertTrue((Boolean) result.get("duplicateDetected"), "Duplicate should be detected");
        assertEquals("Review Potential Duplicate Claim", result.get("taskLabel"), "Task label mismatch");
        assertEquals("fraud_indicators", result.get("triageDimension"), "Triage dimension mismatch");
        assertEquals("awaiting_duplicate_review", result.get("workflowState"), "Workflow state mismatch");

        // Verify external interactions
        verify(claimsHistoryService).searchForDuplicates(policyNumber, riskAddress, dateOfLoss, causeOfLoss);
        verify(taskGeneratorService).createTriageTask(anyMap(), eq(existingClaimId), eq(reporter));
        verify(auditLogService).logEvent(anyString(), eq("DUPLICATE_DETECTED"), anyString());
    }
}
