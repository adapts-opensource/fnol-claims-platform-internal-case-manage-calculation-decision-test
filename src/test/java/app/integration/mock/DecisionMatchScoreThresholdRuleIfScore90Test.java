package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

// External service contracts aligned with infra_io_contracts
interface ComplianceAuditService_s3 { void logComplianceEvent(String claimId, String decision); }
interface DocumentStorage_s3 { void storeDocument(String claimId, String key); }
interface PolicyClaimsDB_dynamodb { void upsertItem(String claimId, Map<String, Object> payload); }
interface ClaimTransformationOrchestration { Map<String, Object> processAndScore(Map<String, Object> claim); }

/**
 * Orchestration engine that coordinates claim transformation and routing decisions.
 * Simulates the orchestration:transformation layer for integration mocking.
 */
class OrchestrationEngine {
    private final ClaimTransformationOrchestration transformation;
    private final ComplianceAuditService_s3 auditService;
    private final DocumentStorage_s3 documentStorage;
    private final PolicyClaimsDB_dynamodb policyClaimsDB;

    OrchestrationEngine(ClaimTransformationOrchestration t, ComplianceAuditService_s3 a, DocumentStorage_s3 d, PolicyClaimsDB_dynamodb db) {
        this.transformation = t;
        this.auditService = a;
        this.documentStorage = d;
        this.policyClaimsDB = db;
    }

    public Map<String, Object> routeClaim(Map<String, Object> claim) {
        Map<String, Object> transformed = transformation.processAndScore(claim);
        String claimId = (String) claim.get("claimId");
        String routingPath = (String) transformed.get("routingPath");

        auditService.logComplianceEvent(claimId, routingPath);
        documentStorage.storeDocument(claimId, routingPath + ".json");
        policyClaimsDB.upsertItem(claimId, transformed);
        return transformed;
    }
}

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingOrchestrationMockTest {

    @Mock
    private ClaimTransformationOrchestration transformation;
    @Mock
    private ComplianceAuditService_s3 auditService;
    @Mock
    private DocumentStorage_s3 documentStorage;
    @Mock
    private PolicyClaimsDB_dynamodb policyClaimsDB;

    @InjectMocks
    private OrchestrationEngine orchestrationEngine;

    @Test
    void decision_match_score_threshold_rule_if_score_90_auto_assign_if_70_89_flag_for_review_if_70_no_match_expected_outcome_clear_routing_path_based_on_score() {
        // Arrange & Act: Score >= 90 -> Auto-assign
        Map<String, Object> claimHighScore = Map.of("claimId", "CLM-001", "matchScore", 95);
        Map<String, Object> transformedHigh = Map.of("claimId", "CLM-001", "matchScore", 95, "routingPath", "AUTO_ASSIGN");
        when(transformation.processAndScore(claimHighScore)).thenReturn(transformedHigh);

        Map<String, Object> resultHigh = orchestrationEngine.routeClaim(claimHighScore);
        assertEquals("AUTO_ASSIGN", resultHigh.get("routingPath"));
        verify(auditService).logComplianceEvent("CLM-001", "AUTO_ASSIGN");
        verify(documentStorage).storeDocument("CLM-001", "AUTO_ASSIGN.json");
        verify(policyClaimsDB).upsertItem("CLM-001", transformedHigh);

        // Arrange & Act: Score 70-89 -> Flag for review
        Map<String, Object> claimMedScore = Map.of("claimId", "CLM-002", "matchScore", 82);
        Map<String, Object> transformedMed = Map.of("claimId", "CLM-002", "matchScore", 82, "routingPath", "FLAG_REVIEW");
        when(transformation.processAndScore(claimMedScore)).thenReturn(transformedMed);

        Map<String, Object> resultMed = orchestrationEngine.routeClaim(claimMedScore);
        assertEquals("FLAG_REVIEW", resultMed.get("routingPath"));
        verify(auditService).logComplianceEvent("CLM-002", "FLAG_REVIEW");
        verify(documentStorage).storeDocument("CLM-002", "FLAG_REVIEW.json");
        verify(policyClaimsDB).upsertItem("CLM-002", transformedMed);

        // Arrange & Act: Score < 70 -> No match
        Map<String, Object> claimLowScore = Map.of("claimId", "CLM-003", "matchScore", 65);
        Map<String, Object> transformedLow = Map.of("claimId", "CLM-003", "matchScore", 65, "routingPath", "NO_MATCH");
        when(transformation.processAndScore(claimLowScore)).thenReturn(transformedLow);

        Map<String, Object> resultLow = orchestrationEngine.routeClaim(claimLowScore);
        assertEquals("NO_MATCH", resultLow.get("routingPath"));
        verify(auditService).logComplianceEvent("CLM-003", "NO_MATCH");
        verify(documentStorage).storeDocument("CLM-003", "NO_MATCH.json");
        verify(policyClaimsDB).upsertItem("CLM-003", transformedLow);
    }
}
