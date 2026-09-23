package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDuplicateIdentificationTest {

    // Mocked infrastructure services per infra_io_contracts
    @Mock
    private PolicyClaimsDB_dynamodb mockPolicyClaimsDB;

    @Mock
    private DocumentStorage_s3 mockDocumentStorage;

    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Initialize the orchestration layer with mocked I/O dependencies
        orchestrator = new ClaimTransformationOrchestrator(mockPolicyClaimsDB, mockDocumentStorage);
    }

    @Test
    void purpose_identify_likely_duplicate_claims_using_multi_field_similarity_scoring() {
        // Arrange: Mock existing claims stored in DynamoDB
        List<Claim> historicalClaims = List.of(
                new Claim("C-EXIST-001", "POL-8821", "2023-10-15", "Alice Chen", "Front-end collision", new BigDecimal("4200.00")),
                new Claim("C-EXIST-002", "POL-9945", "2023-11-02", "Bob Martin", "Pipe leak", new BigDecimal("850.00"))
        );
        when(mockPolicyClaimsDB.queryByPolicyOrClaimant(anyString())).thenReturn(historicalClaims);

        // Input: New claim initiation payload
        Claim newClaim = new Claim("C-NEW-001", "POL-8821", "2023-10-16", "Alice Chen", "Front end collision", new BigDecimal("4150.00"));

        // Act: Execute transformation & duplicate identification routing
        DuplicateAnalysisResult analysis = orchestrator.identifyPotentialDuplicates(newClaim);

        // Assert: Verify multi-field similarity scoring logic
        assertNotNull(analysis, "Analysis result must not be null");
        assertTrue(analysis.likelyDuplicate(), "Claim should be flagged as likely duplicate");
        assertEquals(1, analysis.matchedClaimIds().size(), "Should identify exactly one matching historical claim");
        assertEquals("C-EXIST-001", analysis.matchedClaimIds().get(0), "Must match the correct historical claim ID");
        assertTrue(analysis.similarityScore() >= 0.82, "Multi-field scoring (policy, date, name, desc, amount) must exceed threshold");

        // Verify external I/O mocking boundaries
        verify(mockPolicyClaimsDB, times(1)).queryByPolicyOrClaimant("POL-8821");
        verifyNoInteractions(mockDocumentStorage, "Document storage is not invoked during routing transformation");
    }

    // Minimal domain models for test isolation
    record Claim(String claimId, String policyNumber, String incidentDate, String claimantName, String lossDescription, BigDecimal amount) {}
    record DuplicateAnalysisResult(boolean likelyDuplicate, List<String> matchedClaimIds, double similarityScore) {}

    // Simplified orchestration service under test
    static class ClaimTransformationOrchestrator {
        private final PolicyClaimsDB_dynamodb claimsDB;
        private final DocumentStorage_s3 docStorage;

        ClaimTransformationOrchestrator(PolicyClaimsDB_dynamodb claimsDB, DocumentStorage_s3 docStorage) {
            this.claimsDB = claimsDB;
            this.docStorage = docStorage;
        }

        DuplicateAnalysisResult identifyPotentialDuplicates(Claim newClaim) {
            List<Claim> existing = claimsDB.queryByPolicyOrClaimant(newClaim.policyNumber());
            double maxScore = 0.0;
            String bestMatchId = null;

            for (Claim existing : existing) {
                double score = calculateMultiFieldSimilarity(newClaim, existing);
                if (score > maxScore) {
                    maxScore = score;
                    bestMatchId = existing.claimId();
                }
            }

            boolean isLikelyDuplicate = maxScore >= 0.82;
            List<String> matchedIds = isLikelyDuplicate && bestMatchId != null ? List.of(bestMatchId) : List.of();
            return new DuplicateAnalysisResult(isLikelyDuplicate, matchedIds, maxScore);
        }

        private double calculateMultiFieldSimilarity(Claim c1, Claim c2) {
            double policyMatch = c1.policyNumber().equals(c2.policyNumber()) ? 0.30 : 0.0;
            double dateMatch = c1.incidentDate().equals(c2.incidentDate()) ? 0.20 : 0.0;
            double nameMatch = c1.claimantName().equalsIgnoreCase(c2.claimantName()) ? 0.25 : 0.0;
            double descMatch = c1.lossDescription().toLowerCase().contains(c2.lossDescription().toLowerCase()) ? 0.15 : 0.0;
            double amountDiff = Math.abs(c1.amount().subtract(c2.amount()).doubleValue() / c1.amount().doubleValue());
            double amountMatch = amountDiff < 0.15 ? 0.10 : 0.0;
            return policyMatch + dateMatch + nameMatch + descMatch + amountMatch;
        }
    }

    // Mock interfaces matching infra_io_contracts
    interface PolicyClaimsDB_dynamodb {
        List<Claim> queryByPolicyOrClaimant(String filter);
    }

    interface DocumentStorage_s3 {
        String putObject(String bucketName, String objectKeyPattern, byte[] payload);
    }
}
