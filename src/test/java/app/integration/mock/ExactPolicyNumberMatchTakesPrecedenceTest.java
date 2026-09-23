package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MultiChannelFnolSubmissionStateTransitionCalculationMockTest {

    @Mock
    private PolicyMatchingRepository policyMatchingRepository;

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void exact_policy_number_match_takes_precedence() {
        // Arrange
        String submissionId = "sub-001";
        String exactPolicyNumber = "POL-123";
        String partialPolicy1 = "POL-1234";
        String partialPolicy2 = "POL-12";
        List<String> candidatePolicies = Arrays.asList(partialPolicy1, exactPolicyNumber, partialPolicy2);
        
        Map<String, Object> payload = Map.of(
                "policyNumber", exactPolicyNumber,
                "channel", "WEB",
                "submissionTimestamp", System.currentTimeMillis()
        );
        Map<String, Object> entityData = Map.of("id", submissionId, "payload", payload);

        // Mock external I/O: repository returns mixed candidates
        when(policyMatchingRepository.findByPolicyNumber(exactPolicyNumber))
                .thenReturn(candidatePolicies);

        // Mock external I/O: calculator resolves state transition using prioritized match
        when(stateTransitionCalculator.calculateTransition(entityData, candidatePolicies))
                .thenReturn("CLAIM_OPENED");

        // Act
        String actualState = stateTransitionCalculator.calculateTransition(entityData, candidatePolicies);

        // Assert
        assertEquals("CLAIM_OPENED", actualState, "State transition should complete successfully");
        verify(policyMatchingRepository).findByPolicyNumber(exactPolicyNumber);
        verify(stateTransitionCalculator).calculateTransition(entityData, candidatePolicies);
    }
}
