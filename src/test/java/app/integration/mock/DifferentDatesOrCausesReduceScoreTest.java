package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DifferentDatesOrCausesReduceScoreTest {

    @Mock
    private PolicyClaimsDBService policyClaimsDBService;

    @Mock
    private DocumentStorageService documentStorageService;

    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimTransformationOrchestrator(policyClaimsDBService, documentStorageService);
    }

    @Test
    void different_dates_or_causes_reduce_score() {
        // Arrange: Mock reference claim from DynamoDB
        String refClaimId = "REF-001";
        Map<String, Object> refItem = Map.of(
                "pk", refClaimId,
                "claim_date", "2023-06-15",
                "cause_code", "COLLISION",
                "routing_score", 0.95
        );
        when(policyClaimsDBService.getItem(anyString(), eq("pk"), eq(refClaimId)))
                .thenReturn(refItem);

        // Mock S3 document retrieval
        when(documentStorageService.getObjectUri(anyString(), eq("DocumentStorage/REF-001.json")))
                .thenReturn("s3://DocumentStorage-bucket/DocumentStorage/REF-001.json");

        // Act: Transform new claim with DIFFERENT date and cause
        String newClaimId = "NEW-002";
        Map<String, Object> newItem = Map.of(
                "pk", newClaimId,
                "claim_date", "2024-01-10",
                "cause_code", "THEFT",
                "routing_score", 0.0
        );

        double finalScore = orchestrator.transformAndScore(newItem, refItem);

        // Assert: Score reduction due to different dates/causes
        assertTrue(finalScore < 0.50, "Routing score must be reduced when dates or causes differ");
        assertEquals(0.25, finalScore, 0.001, "Expected reduced score for mismatched attributes");
    }

    // Minimal dependency interfaces for compilation context
    private interface PolicyClaimsDBService {
        Map<String, Object> getItem(String table, String pk, String pkValue);
    }

    private interface DocumentStorageService {
        String getObjectUri(String bucket, String key);
    }

    // Minimal orchestrator for test context
    private static class ClaimTransformationOrchestrator {
        private final PolicyClaimsDBService dbService;
        private final DocumentStorageService docService;

        ClaimTransformationOrchestrator(PolicyClaimsDBService dbService, DocumentStorageService docService) {
            this.dbService = dbService;
            this.docService = docService;
        }

        double transformAndScore(Map<String, Object> newItem, Map<String, Object> refItem) {
            // Simulate transformation logic: score decreases if dates or causes differ
            String newDate = (String) newItem.get("claim_date");
            String refDate = (String) refItem.get("claim_date");
            String newCause = (String) newItem.get("cause_code");
            String refCause = (String) refItem.get("cause_code");

            double score = 1.0;
            if (!newDate.equals(refDate)) score -= 0.4;
            if (!newCause.equals(refCause)) score -= 0.4;
            return Math.max(0.0, score);
        }
    }
}
