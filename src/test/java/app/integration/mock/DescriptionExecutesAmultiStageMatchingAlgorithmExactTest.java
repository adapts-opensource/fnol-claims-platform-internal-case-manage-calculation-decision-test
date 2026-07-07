package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationCalculationTransformTest {

    @Mock
    private MatchingAlgorithmService matchingAlgorithmService;

    private ClaimDataStandardizationCalculationTransformService transformService;

    @BeforeEach
    void setUp() {
        transformService = new ClaimDataStandardizationCalculationTransformService(matchingAlgorithmService);
    }

    @Test
    void description_executes_a_multi_stage_matching_algorithm_exact_match_on_policy_number_then_fuzzy_match_on_risk_address_named_insured_date_of_loss_and_product_form_returns_match_confidence_and_candidate_policies() {
        // Arrange
        String claimId = "claim-std-001";
        Map<String, Object> payload = Map.of(
                "policyNumber", "POL-2023-XYZ",
                "riskAddress", "456 Oak Avenue, Metropolis, NY",
                "namedInsured", "Jane Smith",
                "dateOfLoss", "2023-11-20",
                "productForm", "HO-5"
        );

        Map<String, Object> candidateA = Map.of("policyNumber", "POL-2023-XYZ", "confidenceScore", 0.98);
        Map<String, Object> candidateB = Map.of("policyNumber", "POL-2023-ABC", "confidenceScore", 0.65);
        List<Map<String, Object>> expectedCandidates = List.of(candidateA, candidateB);
        double expectedConfidence = 0.98;

        when(matchingAlgorithmService.executeMultiStageMatching(payload)).thenReturn(
                Map.of("matchConfidence", expectedConfidence, "candidatePolicies", expectedCandidates)
        );

        // Act
        Map<String, Object> transformedData = transformService.processTransformation(claimId, payload);

        // Assert
        assertNotNull(transformedData, "Transformed data should not be null");
        assertEquals(expectedConfidence, transformedData.get("matchConfidence"), "Match confidence should match expected");
        assertEquals(expectedCandidates, transformedData.get("candidatePolicies"), "Candidate policies should match expected");
        verify(matchingAlgorithmService, times(1)).executeMultiStageMatching(payload);
        verifyNoMoreInteractions(matchingAlgorithmService);
    }
}
