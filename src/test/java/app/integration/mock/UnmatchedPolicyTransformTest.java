package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class UnmatchedPolicyTransformTest {

    @Mock
    private PolicyMatchingService policyMatchingService;

    @Mock
    private ClaimStateRepository claimStateRepository;

    @Mock
    private TaskCreationService taskCreationService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private IntakeAggregationService intakeAggregationService;

    @Test
    void transform_policy_match_failure_to_unmatched_state() {
        // Arrange
        String policyNumber = "POL-INVALID";
        String riskAddress = "123 Fake St";
        String namedInsured = "John Doe";
        String dateOfLoss = "2024-05-01";
        String matchCriteria = "PolicyNumber,RiskAddress";

        // Simulate policy match failure during intake
        when(policyMatchingService.evaluateMatch(policyNumber, riskAddress, matchCriteria))
                .thenReturn(Optional.empty());

        // Act
        IntakeProcessingResult result = intakeAggregationService.processIntake(
                policyNumber, riskAddress, namedInsured, dateOfLoss, matchCriteria
        );

        // Assert
        assertEquals("UNMATCHED_FNOL", result.getState(), "Claim state should transform to Unmatched FNOL");
        assertNull(result.getClaimNumber(), "No claim number should be assigned on match failure");
        assertTrue(result.isIntakeOpen(), "Intake must remain open for manual review");
        verify(taskCreationService).createTask("Resolve Policy Match", result.getTrackingId());
        verify(auditLogService).logMatchFailure(policyNumber, matchCriteria);
    }
}
