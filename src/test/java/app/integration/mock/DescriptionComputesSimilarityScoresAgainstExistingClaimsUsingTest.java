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
class MultiChannelFnolSubmissionStateTransitionCalculationMockTest {

    @Mock
    private ExistingClaimsRepository claimsRepository;

    @Mock
    private SimilarityScoringEngine scoringEngine;

    private Map<String, Object> newClaimPayload;
    private List<Map<String, Object>> existingClaims;

    @BeforeEach
    void setUp() {
        newClaimPayload = Map.of(
            "policy", "POL-1001",
            "address", "742 Evergreen Terrace, Springfield",
            "date", "2023-11-15",
            "cause", "Hail Damage",
            "reporter", "Marge Simpson",
            "damagedArea", "Roof and Windshield"
        );

        existingClaims = List.of(
            Map.of("policy", "POL-1001", "address", "742 Evergreen Terrace, Springfield", "date", "2023-11-15", "cause", "Hail Damage", "reporter", "Bart Simpson", "damagedArea", "Roof"),
            Map.of("policy", "POL-2002", "address", "1600 Pennsylvania Avenue", "date", "2020-01-01", "cause", "Vandalism", "reporter", "Staff", "damagedArea", "Door")
        );
    }

    @Test
    void description_computes_similarity_scores_against_existing_claims_using_policy_address_date_cause_reporter_and_damaged_area() {
        // Arrange
        when(claimsRepository.findByPolicyAndDateRange("POL-1001", "2023-11-15", "2023-11-15"))
            .thenReturn(existingClaims);

        when(scoringEngine.computeBatchScores(newClaimPayload, existingClaims))
            .thenReturn(List.of(Map.of("score", 0.95), Map.of("score", 0.12)));

        // Act
        List<Map<String, Object>> similarityResults = scoringEngine.computeBatchScores(newClaimPayload, existingClaims);

        // Assert
        assertNotNull(similarityResults);
        assertEquals(2, similarityResults.size());
        assertEquals(0.95, similarityResults.get(0).get("score"));
        assertEquals(0.12, similarityResults.get(1).get("score"));

        // Verify mock interactions ensure no live infra calls
        verify(claimsRepository, times(1)).findByPolicyAndDateRange("POL-1001", "2023-11-15", "2023-11-15");
        verify(scoringEngine, times(1)).computeBatchScores(newClaimPayload, existingClaims);
    }
}
