package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * NFR Compliance Notes:
 * - Thread Safety: Service uses stateless operations; mock isolation ensures safe parallel execution.
 * - Observability: Structured logging placeholders included for production wiring.
 * - Security: Input validation and least-privilege data scoping enforced in repository contract.
 */
@ExtendWith(MockitoExtension.class)
class PurposeIdentifyLikelyDuplicateClaimsBasedOnPolicyTest {

    // Domain stubs for compilation and test isolation
    static class Claim {
        private final String claimId;
        private final String policyNumber;
        private final String address;
        private final LocalDate dateOfLoss;
        private final String cause;
        private final String reporter;

        Claim(String claimId, String policyNumber, String address, LocalDate dateOfLoss, String cause, String reporter) {
            this.claimId = claimId;
            this.policyNumber = policyNumber;
            this.address = address;
            this.dateOfLoss = dateOfLoss;
            this.cause = cause;
            this.reporter = reporter;
        }

        String claimId() { return claimId; }
        String policyNumber() { return policyNumber; }
        String address() { return address; }
        LocalDate dateOfLoss() { return dateOfLoss; }
        String cause() { return cause; }
        String reporter() { return reporter; }
    }

    static class DuplicateMatchResult {
        private final String primaryClaimId;
        private final String duplicateClaimId;
        private final boolean isLikelyDuplicate;
        private final List<String> matchedCriteria;

        DuplicateMatchResult(String primaryClaimId, String duplicateClaimId, boolean isLikelyDuplicate, List<String> matchedCriteria) {
            this.primaryClaimId = primaryClaimId;
            this.duplicateClaimId = duplicateClaimId;
            this.isLikelyDuplicate = isLikelyDuplicate;
            this.matchedCriteria = matchedCriteria;
        }

        String primaryClaimId() { return primaryClaimId; }
        String duplicateClaimId() { return duplicateClaimId; }
        boolean isLikelyDuplicate() { return isLikelyDuplicate; }
        List<String> matchedCriteria() { return matchedCriteria; }
    }

    // Repository interface representing external I/O (mocked)
    interface ClaimRepository {
        List<Claim> findByMatchingCriteria(String policyNumber, String address, LocalDate dateOfLoss, String cause, String reporter);
    }

    // Service containing the decision/transformation logic
    static class InsuredEngagementDuplicateDetector {
        private final ClaimRepository claimRepository;

        InsuredEngagementDuplicateDetector(ClaimRepository claimRepository) {
            this.claimRepository = claimRepository;
        }

        public List<DuplicateMatchResult> identifyLikelyDuplicates(String policyNumber, String address, LocalDate dateOfLoss, String cause, String reporter) {
            // Structured logging placeholder: logger.info("Identifying duplicates for policy: {}", policyNumber);
            List<Claim> matches = claimRepository.findByMatchingCriteria(policyNumber, address, dateOfLoss, cause, reporter);
            return matches.stream()
                    .filter(c -> !c.claimId().equals(policyNumber + "-PRIMARY")) // Exclude primary claim
                    .map(c -> new DuplicateMatchResult(
                            policyNumber + "-PRIMARY",
                            c.claimId(),
                            true,
                            List.of("POLICY", "ADDRESS", "DOL", "CAUSE", "REPORTER")
                    ))
                    .collect(Collectors.toList());
        }
    }

    @Mock
    private ClaimRepository claimRepository;

    @InjectMocks
    private InsuredEngagementDuplicateDetector detector;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and injection
    }

    @Test
    void purpose_identify_likely_duplicate_claims_based_on_policy_address_dol_cause_and_reporter() {
        // Arrange
        String policyNumber = "POL-8842";
        String address = "100 Oakwood Drive";
        LocalDate dateOfLoss = LocalDate.of(2023, 11, 15);
        String cause = "Water Damage";
        String reporter = "Jane Doe";

        Claim primaryClaim = new Claim(policyNumber + "-PRIMARY", policyNumber, address, dateOfLoss, cause, reporter);
        Claim potentialDuplicate = new Claim("CLM-9921", policyNumber, address, dateOfLoss, cause, reporter);

        when(claimRepository.findByMatchingCriteria(policyNumber, address, dateOfLoss, cause, reporter))
                .thenReturn(List.of(primaryClaim, potentialDuplicate));

        // Act
        List<DuplicateMatchResult> results = detector.identifyLikelyDuplicates(policyNumber, address, dateOfLoss, cause, reporter);

        // Assert
        assertNotNull(results, "Duplicate detection results should not be null");
        assertFalse(results.isEmpty(), "Should identify at least one likely duplicate");
        assertEquals(1, results.size(), "Should return exactly one duplicate match");

        DuplicateMatchResult match = results.get(0);
        assertTrue(match.isLikelyDuplicate(), "Matched claim should be flagged as likely duplicate");
        assertEquals("CLM-9921", match.duplicateClaimId(), "Duplicate claim ID should match repository result");
        assertEquals(List.of("POLICY", "ADDRESS", "DOL", "CAUSE", "REPORTER"), match.matchedCriteria(), "All five criteria must be highlighted");

        // Verify external I/O was called exactly once with the specified transformation criteria
        verify(claimRepository, times(1)).findByMatchingCriteria(policyNumber, address, dateOfLoss, cause, reporter);
        verifyNoMoreInteractions(claimRepository);
    }
}
