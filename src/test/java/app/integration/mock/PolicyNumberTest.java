package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Tests for Insured Engagement & Tracking: decision transformation.
 * Verifies policy_number handling during transformation.
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementTrackingDecisionTransformationTest {

    @Mock
    private ReserveLineRepository reserveLineRepository;

    @Mock
    private PolicyNumberValidator policyNumberValidator;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks and injects them
    }

    @Test
    void policyNumber() {
        // Arrange
        String reserveId = "RES-1001";
        String expectedPolicyNumber = "POL-987654321";

        when(reserveLineRepository.findPolicyNumberByReserveId(reserveId))
                .thenReturn(expectedPolicyNumber);
        when(policyNumberValidator.isValid(expectedPolicyNumber))
                .thenReturn(true);

        // Act
        String actualPolicyNumber = decisionTransformationService.transformPolicyNumber(reserveId);

        // Assert
        assertNotNull(actualPolicyNumber);
        assertEquals(expectedPolicyNumber, actualPolicyNumber);
    }
}
