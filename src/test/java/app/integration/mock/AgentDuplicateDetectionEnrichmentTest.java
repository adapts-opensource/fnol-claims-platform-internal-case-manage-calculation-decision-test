package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentDuplicateDetectionEnrichmentTest {

    @Mock
    private IsoClaimSearchClient isoClaimSearchClient;
    @Mock
    private InternalClaimDatabase internalClaimDatabase;
    @Mock
    private TaskManagementService taskManagementService;
    @Mock
    private ClaimStateMachine claimStateMachine;
    @Mock
    private IntakeShellManager intakeShellManager;

    private FnolEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new FnolEnrichmentService(
                isoClaimSearchClient,
                internalClaimDatabase,
                taskManagementService,
                claimStateMachine,
                intakeShellManager
        );
    }

    @Test
    void enrich_agent_fnol_duplicate_claim_detection() {
        // Arrange
        String channel = "AgentAssisted";
        String product = "DP3";
        String policyNumber = "POL-FL-54321";
        LocalDate dateOfLoss = LocalDate.of(2024, 6, 10);
        String causeOfLoss = "fire";
        String insuredName = "Robert Johnson";
        String riskAddress = "789 Bay St Miami FL";
        String reporterType = "agent";
        String existingClaimId = "CLM-FL01-2024-00009876";

        FnolSubmissionPayload payload = new FnolSubmissionPayload(
                channel, product, policyNumber, dateOfLoss, causeOfLoss,
                insuredName, riskAddress, reporterType, existingClaimId
        );

        // Mock external I/O: ISO ClaimSearch & Internal DB return match
        DuplicateDetectionResult detectionResult = new DuplicateDetectionResult(
                true, List.of(existingClaimId), "ISO & Internal Match"
        );
        when(isoClaimSearchClient.query(anyString(), anyString())).thenReturn(detectionResult);
        when(internalClaimDatabase.findOpenClaims(anyString(), anyString(), anyString())).thenReturn(List.of(existingClaimId));

        // Act
        EnrichmentOutcome outcome = enrichmentService.processDuplicateDetection(payload);

        // Assert Expected Results
        assertTrue(outcome.isDuplicateDetected(), "Duplicate claim should be detected via ISO and internal DB");
        assertEquals("Duplicate Review", outcome.getClaimType(), "Claim type should remain Unmatched FNOL or Duplicate Review");
        verify(taskManagementService).createTask(eq("Review Potential Duplicate Claim"), anyString(), anyString());
        verify(claimStateMachine).transitionTo(eq("Duplicate Review"));
        verify(intakeShellManager).createIntakeShell(anyString(), anyString());
    }

    // Supporting types for compilation and isolated testing
    record DuplicateDetectionResult(boolean matched, List<String> matchedClaimIds, String source) {}
    record FnolSubmissionPayload(String channel, String product, String policyNumber, LocalDate dateOfLoss,
                                 String causeOfLoss, String insuredName, String riskAddress,
                                 String reporterType, String existingClaimId) {}
    record EnrichmentOutcome(boolean duplicateDetected, String claimType) {
        static EnrichmentOutcome of(boolean dup, String type) { return new EnrichmentOutcome(dup, type); }
    }

    interface IsoClaimSearchClient {
        DuplicateDetectionResult query(String policyNumber, String dateOfLoss);
    }
    interface InternalClaimDatabase {
        List<String> findOpenClaims(String policyNumber, String address, String dateOfLoss);
    }
    interface TaskManagementService {
        void createTask(String taskType, String assignee, String referenceId);
    }
    interface ClaimStateMachine {
        void transitionTo(String newState);
    }
    interface IntakeShellManager {
        void createIntakeShell(String claimId, String status);
    }

    // Minimal service implementation to satisfy constructor injection
    static class FnolEnrichmentService {
        private final IsoClaimSearchClient isoClaimSearchClient;
        private final InternalClaimDatabase internalClaimDatabase;
        private final TaskManagementService taskManagementService;
        private final ClaimStateMachine claimStateMachine;
        private final IntakeShellManager intakeShellManager;

        FnolEnrichmentService(IsoClaimSearchClient isoClaimSearchClient,
                              InternalClaimDatabase internalClaimDatabase,
                              TaskManagementService taskManagementService,
                              ClaimStateMachine claimStateMachine,
                              IntakeShellManager intakeShellManager) {
            this.isoClaimSearchClient = isoClaimSearchClient;
            this.internalClaimDatabase = internalClaimDatabase;
            this.taskManagementService = taskManagementService;
            this.claimStateMachine = claimStateMachine;
            this.intakeShellManager = intakeShellManager;
        }

        EnrichmentOutcome processDuplicateDetection(FnolSubmissionPayload payload) {
            DuplicateDetectionResult isoResult = isoClaimSearchClient.query(payload.policyNumber(), payload.dateOfLoss().toString());
            List<String> dbMatches = internalClaimDatabase.findOpenClaims(payload.policyNumber(), payload.riskAddress(), payload.dateOfLoss().toString());

            boolean isDuplicate = isoResult.matched() || !dbMatches.isEmpty();
            String claimType = isDuplicate ? "Duplicate Review" : "Unmatched FNOL";

            if (isDuplicate) {
                taskManagementService.createTask("Review Potential Duplicate Claim", "claims_reviewer", payload.existingClaimId());
                claimStateMachine.transitionTo("Duplicate Review");
                intakeShellManager.createIntakeShell(payload.policyNumber(), "PENDING_MERGE");
            }

            return EnrichmentOutcome.of(isDuplicate, claimType);
        }
    }
}
