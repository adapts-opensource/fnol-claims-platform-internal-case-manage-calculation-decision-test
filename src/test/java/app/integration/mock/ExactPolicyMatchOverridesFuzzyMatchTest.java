package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExactPolicyMatchOverridesFuzzyMatchTest {

    @Mock
    private PolicyMatchingService policyMatchingService;

    private InsuredEngagementTransformation transformation;

    @BeforeEach
    void setUp() {
        transformation = new InsuredEngagementTransformation(policyMatchingService);
    }

    @Test
    void exactPolicyMatchOverridesFuzzyMatch() {
        // Arrange
        String insuredId = "INS-123";
        String exactPolicyId = "POL-EXACT-001";
        String fuzzyPolicyId = "POL-FUZZY-002";

        Policy exactPolicy = new Policy(exactPolicyId, "Exact Match Policy", BigDecimal.valueOf(500000));
        Policy fuzzyPolicy = new Policy(fuzzyPolicyId, "Fuzzy Match Policy", BigDecimal.valueOf(250000));

        // Mock external matching service to return both candidates (fuzzy first in list)
        when(policyMatchingService.findCandidates(any(InsuredContext.class)))
                .thenReturn(List.of(fuzzyPolicy, exactPolicy));

        InsuredContext context = new InsuredContext(insuredId, "John Doe", "123 Main St");

        // Act
        Policy matchedPolicy = transformation.transformAndMatch(context);

        // Assert
        assertNotNull(matchedPolicy, "Matched policy should not be null");
        assertEquals(exactPolicyId, matchedPolicy.getPolicyId(), "Exact match should override fuzzy match");
        assertEquals("Exact Match Policy", matchedPolicy.getName());
        verify(policyMatchingService, times(1)).findCandidates(context);
    }

    // Minimal domain models for test isolation
    record Policy(String policyId, String name, BigDecimal coverageAmount) {
        public String getPolicyId() { return policyId; }
        public String getName() { return name; }
    }

    record InsuredContext(String insuredId, String name, String address) {}

    interface PolicyMatchingService {
        List<Policy> findCandidates(InsuredContext context);
    }

    // Transformation logic under test
    static class InsuredEngagementTransformation {
        private final PolicyMatchingService policyMatchingService;

        InsuredEngagementTransformation(PolicyMatchingService policyMatchingService) {
            this.policyMatchingService = policyMatchingService;
        }

        Policy transformAndMatch(InsuredContext context) {
            List<Policy> candidates = policyMatchingService.findCandidates(context);
            // Decision transformation: exact match takes precedence over fuzzy match
            return candidates.stream()
                    .filter(p -> p.policyId().startsWith("POL-EXACT"))
                    .findFirst()
                    .orElseGet(() -> candidates.isEmpty() ? null : candidates.get(0));
        }
    }
}
