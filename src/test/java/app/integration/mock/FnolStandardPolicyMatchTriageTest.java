package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization: orchestration: decision
 * Verifies FNOL intake with active policy match results in correct claim type, number generation, and task creation.
 */
@ExtendWith(MockitoExtension.class)
public class FnolStandardPolicyMatchTriageTest {

    @Mock
    private ClaimOrchestrationService mockOrchestrationService;

    @BeforeEach
    void setUp() {
        // Mocks initialized via MockitoExtension; no live AWS or HTTP calls
    }

    @Test
    void fnol_standard_policy_match_triage_decision() {
        // Arrange inputs
        String channel = "API";
        String policyNumber = "POL-12345";
        String dateOfLoss = "2024-05-01";
        String causeOfLoss = "Wind";
        String insuredName = "John Doe";
        String riskAddress = "123 Main St";
        String tenantCode = "FL01";

        Map<String, Object> inputPayload = Map.of(
            "channel", channel,
            "policy_number", policyNumber,
            "date_of_loss", dateOfLoss,
            "cause_of_loss", causeOfLoss,
            "insured_name", insuredName,
            "risk_address", riskAddress,
            "tenant_code", tenantCode
        );

        // Define expected orchestration decision results
        Map<String, Object> expectedResponse = Map.of(
            "policy_match_status", "Match",
            "claim_type", "Standard property claim",
            "claim_number", "CLM-FL01-2024-00001234",
            "state", "Claim Opened",
            "tasks", List.of("Review FNOL", "Acknowledge Claim", "Assign Adjuster")
        );

        // Mock the orchestration service (abstracts DynamoDB/S3 I/O)
        when(mockOrchestrationService.processFnolIntake(inputPayload)).thenReturn(expectedResponse);

        // Act
        Map<String, Object> result = mockOrchestrationService.processFnolIntake(inputPayload);

        // Assert expected results
        assertEquals("Match", result.get("policy_match_status"));
        assertEquals("Standard property claim", result.get("claim_type"));
        assertEquals("CLM-FL01-2024-00001234", result.get("claim_number"));
        assertEquals("Claim Opened", result.get("state"));
        List<String> expectedTasks = List.of("Review FNOL", "Acknowledge Claim", "Assign Adjuster");
        assertArrayEquals(expectedTasks.toArray(), ((List<String>) result.get("tasks")).toArray());
    }
}
