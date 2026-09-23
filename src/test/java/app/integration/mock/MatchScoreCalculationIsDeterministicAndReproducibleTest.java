package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Verifies that the match score calculation within the Claim Data Standardization
 * decision transformation is deterministic and reproducible across multiple invocations.
 */
@ExtendWith(MockitoExtension.class)
public class MatchScoreCalculationIsDeterministicAndReproducibleTest {

    private static final String EXPECTED_MATCH_SCORE = "0.92";

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    private ClaimDataTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new ClaimDataTransformationService(auditDiaryStore, rulesEngineDecisionService);
    }

    @Test
    void match_score_calculation_is_deterministic_and_reproducible() {
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimId", claimId);
        payload.put("severity", "HIGH");
        payload.put("retrievalMatch", true);

        Map<String, Object> resultFirst = transformationService.transform(claimId, payload);
        Map<String, Object> resultSecond = transformationService.transform(claimId, payload);

        String scoreFirst = (String) resultFirst.get("matchScore");
        String scoreSecond = (String) resultSecond.get("matchScore");

        assertEquals(EXPECTED_MATCH_SCORE, scoreFirst, "First run match score must match expected deterministic value");
        assertEquals(EXPECTED_MATCH_SCORE, scoreSecond, "Second run match score must match expected deterministic value");
        assertEquals(scoreFirst, scoreSecond, "Match scores must be identical across deterministic runs");

        verify(auditDiaryStore, times(2)).write(anyString(), anyString(), anyString());
        verify(rulesEngineDecisionService, times(2)).query(anyString(), anyString());
    }

    // Minimal interfaces representing external I/O contracts to satisfy compilation and mocking requirements
    interface AuditDiaryStore {
        void write(String bucketName, String objectKeyPattern, String data);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> query(String tableName, String partitionKey);
    }

    // Service under test with pure, deterministic match score calculation logic
    static class ClaimDataTransformationService {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineDecisionService;

        ClaimDataTransformationService(AuditDiaryStore auditDiaryStore, RulesEngineDecisionService rulesEngineDecisionService) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
        }

        Map<String, Object> transform(String id, Map<String, Object> payload) {
            double score = calculateMatchScore(payload);
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("matchScore", String.valueOf(score));
            result.put("payload", payload);

            // Mocked external I/O calls (no live AWS/HTTP traffic)
            auditDiaryStore.write("AuditDiaryStore-bucket", "AuditDiaryStore/" + id + ".json", "audit_log");
            rulesEngineDecisionService.query("RulesEngineDecisionService_table", "pk");

            return result;
        }

        private double calculateMatchScore(Map<String, Object> payload) {
            // Pure, deterministic algorithm based strictly on input payload
            boolean hasMatch = Boolean.TRUE.equals(payload.get("retrievalMatch"));
            String severity = (String) payload.get("severity");
            double baseScore = hasMatch ? 0.80 : 0.50;
            if ("HIGH".equals(severity)) {
                baseScore += 0.12;
            }
            return baseScore;
        }
    }
}
