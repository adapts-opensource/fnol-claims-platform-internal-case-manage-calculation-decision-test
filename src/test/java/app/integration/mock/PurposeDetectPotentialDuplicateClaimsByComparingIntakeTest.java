package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private CompositeKeyGenerator compositeKeyGenerator;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private ScoringModel scoringModel;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimDataStandardizationDecisionTransformationService service;

    @BeforeEach
    void setUp() {
        service = new ClaimDataStandardizationDecisionTransformationService(
                compositeKeyGenerator, rulesEngineDecisionService, scoringModel, auditDiaryStore
        );
    }

    @Test
    void purpose_detect_potential_duplicate_claims_by_comparing_intake_data_against_existing_claims_using_a_composite_key_and_scoring_model() {
        // Arrange: Prepare intake payload and mock external dependencies
        String claimId = "claim-123";
        Map<String, Object> intakePayload = Map.of(
                "policyNumber", "POL-456",
                "insuredName", "John Doe",
                "dateOfLoss", "2023-10-01",
                "claimType", "AUTO"
        );

        String compositeKey = "POL-456|John Doe|AUTO|2023-10-01";
        when(compositeKeyGenerator.generate(anyMap())).thenReturn(compositeKey);

        Map<String, Object> existingClaim = Map.of(
                "id", "claim-789",
                "compositeKey", compositeKey,
                "status", "OPEN"
        );
        when(rulesEngineDecisionService.queryByCompositeKey(compositeKey))
                .thenReturn(List.of(existingClaim));

        double similarityScore = 0.95;
        double threshold = 0.85;
        when(scoringModel.calculateSimilarity(intakePayload, existingClaim)).thenReturn(similarityScore);

        // Act: Execute duplicate detection logic
        DuplicateDetectionResult result = service.detectPotentialDuplicates(claimId, intakePayload, threshold);

        // Assert: Verify composite key generation, DynamoDB query, scoring, and audit logging
        assertNotNull(result);
        assertTrue(result.isDuplicateFound());
        assertEquals(1, result.getMatchedClaimIds().size());
        assertEquals("claim-789", result.getMatchedClaimIds().get(0));
        assertEquals(similarityScore, result.getHighestSimilarityScore(), 0.001);

        verify(compositeKeyGenerator).generate(intakePayload);
        verify(rulesEngineDecisionService).queryByCompositeKey(compositeKey);
        verify(scoringModel).calculateSimilarity(intakePayload, existingClaim);
        verify(auditDiaryStore).logAuditEvent(anyMap());
    }

    // Minimal interfaces representing infra I/O contracts
    interface CompositeKeyGenerator {
        String generate(Map<String, Object> payload);
    }

    interface RulesEngineDecisionService {
        List<Map<String, Object>> queryByCompositeKey(String compositeKey);
    }

    interface ScoringModel {
        double calculateSimilarity(Map<String, Object> intake, Map<String, Object> existing);
    }

    interface AuditDiaryStore {
        void logAuditEvent(Map<String, Object> event);
    }

    // Service under test
    static class ClaimDataStandardizationDecisionTransformationService {
        private final CompositeKeyGenerator compositeKeyGenerator;
        private final RulesEngineDecisionService rulesEngineDecisionService;
        private final ScoringModel scoringModel;
        private final AuditDiaryStore auditDiaryStore;

        ClaimDataStandardizationDecisionTransformationService(CompositeKeyGenerator compositeKeyGenerator,
                                                              RulesEngineDecisionService rulesEngineDecisionService,
                                                              ScoringModel scoringModel,
                                                              AuditDiaryStore auditDiaryStore) {
            this.compositeKeyGenerator = compositeKeyGenerator;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
            this.scoringModel = scoringModel;
            this.auditDiaryStore = auditDiaryStore;
        }

        DuplicateDetectionResult detectPotentialDuplicates(String claimId, Map<String, Object> intakePayload, double threshold) {
            // Input validation (NFR: input_validation)
            if (intakePayload == null || intakePayload.isEmpty()) {
                throw new IllegalArgumentException("Intake payload must not be empty");
            }

            String compositeKey = compositeKeyGenerator.generate(intakePayload);
            List<Map<String, Object>> existingClaims = rulesEngineDecisionService.queryByCompositeKey(compositeKey);
            
            double highestScore = 0.0;
            String matchedClaimId = null;

            for (Map<String, Object> existing : existingClaims) {
                double score = scoringModel.calculateSimilarity(intakePayload, existing);
                if (score > highestScore) {
                    highestScore = score;
                    matchedClaimId = (String) existing.get("id");
                }
            }

            // Structured logging & audit trail (NFR: structured_logging, compliance: gdpr/soc2)
            auditDiaryStore.logAuditEvent(Map.of(
                    "claimId", claimId,
                    "compositeKey", compositeKey,
                    "highestSimilarityScore", highestScore,
                    "threshold", threshold,
                    "timestamp", System.currentTimeMillis()
            ));

            boolean isDuplicate = matchedClaimId != null && highestScore >= threshold;
            return new DuplicateDetectionResult(isDuplicate, matchedClaimId, highestScore);
        }
    }

    // Result record
    record DuplicateDetectionResult(boolean isDuplicateFound, String matchedClaimId, double highestSimilarityScore) {
        List<String> getMatchedClaimIds() {
            return matchedClaimId != null ? List.of(matchedClaimId) : List.of();
        }
    }
}
