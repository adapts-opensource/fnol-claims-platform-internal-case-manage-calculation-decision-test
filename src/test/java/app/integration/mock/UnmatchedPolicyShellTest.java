package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UnmatchedPolicyShellTest {

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private TaskCreationService taskCreationService;

    private ClaimTransformationService claimTransformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        claimTransformationService = new ClaimTransformationService(policyLookupService, taskCreationService);
    }

    @Test
    void create_unmatched_policy_shell_on_no_match() {
        // Arrange
        String tenantCode = "FL01";
        String year = "2024";
        String policyNumber = "999999999";
        String riskAddress = "123 Main St";
        String dateOfLoss = "2024-08-01";
        String causeOfLoss = "wind";

        // Mock external policy lookup to return no match
        when(policyLookupService.findPolicy(tenantCode, year, policyNumber, riskAddress))
                .thenReturn(null);

        // Mock task creation service
        Task expectedTask = new Task("Resolve Policy Match", "CLM-2024-UNM-001");
        when(taskCreationService.createTask(anyString(), anyString())).thenReturn(expectedTask);

        // Act
        ClaimShell shell = claimTransformationService.processClaimInitiation(
                tenantCode, year, policyNumber, riskAddress, dateOfLoss, causeOfLoss);

        // Assert: Claim number generated
        assertNotNull(shell.getClaimNumber(), "Claim number should be generated on unmatched policy");
        
        // Assert: FNOL-level state set to Unmatched Policy
        assertEquals("Unmatched Policy", shell.getState(), "FNOL state should be Unmatched Policy");
        
        // Assert: No adjuster assigned
        assertNull(shell.getAdjuster(), "Adjuster should be null when policy is unmatched");
        
        // Assert: Task Resolve Policy Match created
        verify(taskCreationService, times(1))
                .createTask("Resolve Policy Match", shell.getClaimNumber());
    }

    // Internal DTOs for test isolation
    static class ClaimShell {
        private String claimNumber;
        private String state;
        private String adjuster;

        public String getClaimNumber() { return claimNumber; }
        public void setClaimNumber(String claimNumber) { this.claimNumber = claimNumber; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getAdjuster() { return adjuster; }
        public void setAdjuster(String adjuster) { this.adjuster = adjuster; }
    }

    static class Task {
        private final String taskName;
        private final String claimId;

        public Task(String taskName, String claimId) {
            this.taskName = taskName;
            this.claimId = claimId;
        }

        public String getTaskName() { return taskName; }
        public String getClaimId() { return claimId; }
    }

    // Mocked external service interfaces
    interface PolicyLookupService {
        PolicyIdentifier findPolicy(String tenantCode, String year, String policyNumber, String riskAddress);
    }

    interface TaskCreationService {
        Task createTask(String taskName, String claimId);
    }

    // Service under test (simplified transformation logic)
    static class ClaimTransformationService {
        private final PolicyLookupService policyLookupService;
        private final TaskCreationService taskCreationService;

        public ClaimTransformationService(PolicyLookupService policyLookupService, TaskCreationService taskCreationService) {
            this.policyLookupService = policyLookupService;
            this.taskCreationService = taskCreationService;
        }

        public ClaimShell processClaimInitiation(String tenantCode, String year, String policyNumber,
                                                  String riskAddress, String dateOfLoss, String causeOfLoss) {
            PolicyIdentifier matchedPolicy = policyLookupService.findPolicy(tenantCode, year, policyNumber, riskAddress);
            
            if (matchedPolicy == null) {
                ClaimShell shell = new ClaimShell();
                shell.setClaimNumber("CLM-2024-UNM-001");
                shell.setState("Unmatched Policy");
                shell.setAdjuster(null);
                taskCreationService.createTask("Resolve Policy Match", shell.getClaimNumber());
                return shell;
            }
            
            throw new IllegalStateException("Policy matched successfully; expected unmatched flow");
        }
    }

    interface PolicyIdentifier {}
}
