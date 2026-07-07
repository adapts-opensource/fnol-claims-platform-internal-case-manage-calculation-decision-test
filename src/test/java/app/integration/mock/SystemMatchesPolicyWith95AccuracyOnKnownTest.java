package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration tests for Insured Engagement & Tracking: decision transformation.
 * Verifies policy matching logic against mocked persistence layers.
 */
@ExtendWith(MockitoExtension.class)
public class PolicyMatchingTransformationMockTest {

    private static final String KNOWN_INSURED_ID = "INS-789";
    private static final String EXPECTED_POLICY_ID = "POL-100";
    private static final BigDecimal ACCURACY_THRESHOLD = new BigDecimal("0.95");
    private static final String ACTIVE_STATUS = "Active";

    @Mock
    private PolicyRepository policyRepository;

    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        decisionTransformationService = new DecisionTransformationService(policyRepository);
    }

    @Test
    void system_matches_policy_with_95_accuracy_on_known_active_policies() {
        // Arrange: Setup known active policy data to simulate DynamoDB response
        PolicyData expectedPolicy = new PolicyData();
        expectedPolicy.setPolicyId(EXPECTED_POLICY_ID);
        expectedPolicy.setStatus(ACTIVE_STATUS);
        expectedPolicy.setInsuredId(KNOWN_INSURED_ID);

        when(policyRepository.findByInsuredId(KNOWN_INSURED_ID))
                .thenReturn(Optional.of(expectedPolicy));

        // Act: Trigger transformation and matching decision
        MatchingResult result = decisionTransformationService.transformAndMatchPolicy(KNOWN_INSURED_ID);

        // Assert: Verify match exists and confidence meets >95% accuracy requirement
        assertNotNull(result, "Result should not be null for known insured");
        assertTrue(result.isMatched(), "System should match known active policy");
        assertEquals(EXPECTED_POLICY_ID, result.getMatchedPolicyId(), "Matched policy ID should correspond to known policy");
        assertTrue(result.getConfidenceScore().compareTo(ACCURACY_THRESHOLD) >= 0,
                "Confidence score must be >= 95% accuracy threshold for known active policies");

        // Verify single interaction with persistence layer
        verify(policyRepository, times(1)).findByInsuredId(KNOWN_INSURED_ID);
    }
}
