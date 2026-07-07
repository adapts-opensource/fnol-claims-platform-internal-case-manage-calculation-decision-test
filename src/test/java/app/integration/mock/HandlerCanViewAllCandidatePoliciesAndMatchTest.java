package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * JUnit 5 test class for Multi-Channel FNOL Submission:decision:validation.
 * Verifies handler behavior for viewing candidate policies and match scores.
 */
public class MultiChannelFnolSubmissionDecisionValidationHandlerTest {

    @Mock
    private PolicyCoverageValidator policyCoverageValidator;

    @InjectMocks
    private MultiChannelFnolSubmissionDecisionValidationHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void handler_can_view_all_candidate_policies_and_match_scores() {
        // Arrange
        String fnolId = "fnol-mock-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("vehicleMake", "Toyota");
        payload.put("vehicleModel", "Camry");
        payload.put("incidentDate", "2023-10-01");

        List<Map<String, Object>> expectedCandidates = new ArrayList<>();
        
        Map<String, Object> policyHighMatch = new HashMap<>();
        policyHighMatch.put("policyId", "pol-123");
        policyHighMatch.put("matchScore", 0.95);
        policyHighMatch.put("coverageType", "Comprehensive");
        expectedCandidates.add(policyHighMatch);

        Map<String, Object> policyLowMatch = new HashMap<>();
        policyLowMatch.put("policyId", "pol-456");
        policyLowMatch.put("matchScore", 0.35);
        policyLowMatch.put("coverageType", "Liability Only");
        expectedCandidates.add(policyLowMatch);

        when(policyCoverageValidator.getCandidatePolicies(anyString(), anyMap()))
                .thenReturn(expectedCandidates);

        // Act
        Map<String, Object> validationResult = handler.processValidationDecision(fnolId, payload);

        // Assert
        assertNotNull(validationResult, "Validation result should not be null");
        assertTrue(validationResult.containsKey("candidatePolicies"), "Result must contain candidate policies");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actualCandidates = (List<Map<String, Object>>) validationResult.get("candidatePolicies");
        
        assertEquals(2, actualCandidates.size(), "Should return all candidate policies");
        
        // Verify match scores are present and correct
        assertEquals(0.95, ((Map<String, Object>) actualCandidates.get(0)).get("matchScore"), 0.001);
        assertEquals(0.35, ((Map<String, Object>) actualCandidates.get(1)).get("matchScore"), 0.001);

        // Verify external I/O was called correctly
        verify(policyCoverageValidator, times(1))
                .getCandidatePolicies(eq(fnolId), anyMap());
    }
}
