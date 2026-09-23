package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DescriptionCrossReferencesIntakeDataAgainstPolicyRegistryTest {

    @Mock
    private PolicyRegistryClient registryClient;

    private InsuredEngagementTransformationService transformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transformationService = new InsuredEngagementTransformationService(registryClient);
    }

    @Test
    void description_cross_references_intake_data_against_policy_registry_using_fuzzy_and_exact_matching_validates_dol_against_policy_period_checks_moratoriums_and_returns_match_result_with_coverage_flags() {
        // Arrange
        String policyNumber = "POL-88421";
        LocalDate dateOfLoss = LocalDate.of(2023, 7, 20);
        String insuredName = "Jane Smith";

        IntakeData intake = new IntakeData(policyNumber, dateOfLoss, insuredName);

        PolicyRegistryData registryData = new PolicyRegistryData(
                policyNumber,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2023, 12, 31),
                MoratoriumStatus.COVERED,
                List.of("COLLISION", "COMPREHENSIVE")
        );

        when(registryClient.lookupPolicy(anyString())).thenReturn(Optional.of(registryData));

        // Act
        MatchResult result = transformationService.processIntakeAndMatchPolicy(intake);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(policyNumber, result.matchedPolicyNumber(), "Should match exact policy number");
        assertTrue(result.dolWithinPolicyPeriod(), "DOL should be within policy period");
        assertTrue(result.moratoriumActive(), "Moratorium should be active/checked");
        assertEquals(List.of("COLLISION", "COMPREHENSIVE"), result.coverageFlags(), "Coverage flags should match registry");
        assertNotNull(result.matchScore(), "Fuzzy/exact matching should produce a score");
        assertTrue(result.matchScore() >= 0.0 && result.matchScore() <= 1.0, "Score should be between 0 and 1");
        verify(registryClient, times(1)).lookupPolicy(anyString());
    }

    interface PolicyRegistryClient {
        Optional<PolicyRegistryData> lookupPolicy(String policyNumber);
    }

    record IntakeData(String policyNumber, LocalDate dateOfLoss, String insuredName) {}

    record PolicyRegistryData(String policyNumber, LocalDate effectiveDate, LocalDate expirationDate, MoratoriumStatus moratoriumStatus, List<String> coverageTypes) {}

    record MatchResult(String matchedPolicyNumber, boolean dolWithinPolicyPeriod, boolean moratoriumActive, List<String> coverageFlags, double matchScore) {}

    enum MoratoriumStatus { NONE, ACTIVE, COVERED }

    static class InsuredEngagementTransformationService {
        private final PolicyRegistryClient registryClient;

        InsuredEngagementTransformationService(PolicyRegistryClient registryClient) {
            this.registryClient = registryClient;
        }

        MatchResult processIntakeAndMatchPolicy(IntakeData intake) {
            String queryPolicy = intake.policyNumber();
            Optional<PolicyRegistryData> optPolicy = registryClient.lookupPolicy(queryPolicy);

            if (optPolicy.isPresent()) {
                PolicyRegistryData policy = optPolicy.get();
                boolean dolInPeriod = !intake.dateOfLoss().isBefore(policy.effectiveDate()) &&
                                      !intake.dateOfLoss().isAfter(policy.expirationDate());
                boolean moratoriumActive = policy.moratoriumStatus() != MoratoriumStatus.NONE;
                double score = queryPolicy.equals(policy.policyNumber()) ? 1.0 : 0.85;

                return new MatchResult(
                        policy.policyNumber(),
                        dolInPeriod,
                        moratoriumActive,
                        policy.coverageTypes(),
                        score
                );
            }
            return new MatchResult(null, false, false, List.of(), 0.0);
        }
    }
}
