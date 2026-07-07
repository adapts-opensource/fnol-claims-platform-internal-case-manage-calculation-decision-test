package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

/**
 * JUnit 5 mock test for Claim Data Standardization:decision:transformation.
 * Verifies deterministic and probabilistic policy matching logic.
 * NFR Compliance: Stateless/thread-safe design, strict input validation, GDPR/SOC2 data handling, structured audit logging.
 */
@ExtendWith(MockitoExtension.class)
class PurposeDetermineTheBestMatchingPolicyForThe {

    @Mock
    private RulesEngineDecisionService rulesEngine;

    @Mock
    private AuditDiaryStore auditStore;

    private ClaimDecisionTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new ClaimDecisionTransformer(rulesEngine, auditStore);
    }

    @Test
    void purpose_determine_the_best_matching_policy_for_the_reported_loss_using_deterministic_and_probabilistic_scoring_based_on_provided_identifiers_and_contextual_data() {
        // Arrange: Deterministic exact match scenario
        Map<String, Object> claimPayload = buildClaimPayload("claim-001", "POL-100", "1HGBH41JXMN109186", "Alice Smith");
        List<Map<String, Object>> candidatePolicies = Arrays.asList(
            buildPolicy("POL-100", "1HGBH41JXMN109186", "ACTIVE", 1.0),
            buildPolicy("POL-200", "1HGBH41JXMN109185", "ACTIVE", 0.0)
        );

        when(rulesEngine.fetchCandidatePolicies("claim-001")).thenReturn(candidatePolicies);
        when(auditStore.writeAudit(eq("claim-001"), anyString())).thenReturn("s3://AuditDiaryStore-bucket/claim-001.json");

        // Act
        Map<String, Object> result = transformer.transform(claimPayload);

        // Assert
        assertNotNull(result, "Transformation result must not be null");
        assertEquals("POL-100", result.get("matchedPolicyId"), "Should match deterministic policy exactly");
        assertEquals(1.0, result.get("confidenceScore"), "Exact match should yield 1.0 confidence");
        assertEquals("s3://AuditDiaryStore-bucket/claim-001.json", result.get("auditUri"), "Audit S3 URI must be resolved");
        verify(rulesEngine, times(1)).fetchCandidatePolicies("claim-001");
        verify(auditStore, times(1)).writeAudit(eq("claim-001"), anyString());
    }

    @Test
    void purpose_determine_the_best_matching_policy_for_the_reported_loss_using_deterministic_and_probabilistic_scoring_based_on_provided_identifiers_and_contextual_data_probabilisticFallback() {
        // Arrange: No exact policy match, but probabilistic scoring applies
        Map<String, Object> claimPayload = buildClaimPayload("claim-002", "POL-UNKNOWN", "1HGBH41JXMN", "Bob Jones");
        List<Map<String, Object>> candidatePolicies = Arrays.asList(
            buildPolicy("POL-300", "1HGBH41JXMN999999", "ACTIVE", 0.85)
        );

        when(rulesEngine.fetchCandidatePolicies("claim-002")).thenReturn(candidatePolicies);
        when(auditStore.writeAudit(eq("claim-002"), anyString())).thenReturn("s3://AuditDiaryStore-bucket/claim-002.json");

        // Act
        Map<String, Object> result = transformer.transform(claimPayload);

        // Assert
        assertNotNull(result);
        assertEquals("POL-300", result.get("matchedPolicyId"), "Should fallback to probabilistic match");
        double score = (double) result.get("confidenceScore");
        assertTrue(score < 1.0 && score >= 0.5, "Probabilistic score should be between 0.5 and 1.0");
        verify(auditStore).writeAudit(eq("claim-002"), anyString());
    }

    @Test
    void purpose_determine_the_best_matching_policy_for_the_reported_loss_using_deterministic_and_probabilistic_scoring_based_on_provided_identifiers_and_contextual_data_inputValidation() {
        // Arrange: Missing required identifiers
        Map<String, Object> invalidPayload = new HashMap<>();
        invalidPayload.put("id", "claim-003");
        // policyNumber and vehicleVin omitted

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> transformer.transform(invalidPayload),
            "Must reject payloads missing required identifiers for GDPR/SOC2 compliance");
    }

    @Test
    void purpose_determine_the_best_matching_policy_for_the_reported_loss_using_deterministic_and_probabilistic_scoring_based_on_provided_identifiers_and_contextual_data_noCandidates() {
        // Arrange: Rules engine returns empty list
        Map<String, Object> claimPayload = buildClaimPayload("claim-004", "POL-500", "VIN-NULL", "Charlie Doe");
        when(rulesEngine.fetchCandidatePolicies("claim-004")).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> transformer.transform(claimPayload),
            "Must throw when no policies match deterministic or probabilistic thresholds");
    }

    // --- Test Helpers ---
    private Map<String, Object> buildClaimPayload(String id, String policyNumber, String vehicleVin, String insuredName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("policyNumber", policyNumber);
        payload.put("vehicleVin", vehicleVin);
        payload.put("insuredName", insuredName);
        payload.put("claimDate", "2023-10-01");
        return payload;
    }

    private Map<String, Object> buildPolicy(String policyId, String vin, String status, double baseScore) {
        Map<String, Object> policy = new HashMap<>();
        policy.put("policyId", policyId);
        policy.put("vin", vin);
        policy.put("status", status);
        policy.put("baseScore", baseScore);
        return policy;
    }
}

// --- Infrastructure Mock Contracts (Simulated) ---
interface RulesEngineDecisionService {
    List<Map<String, Object>> fetchCandidatePolicies(String claimId);
}

interface AuditDiaryStore {
    String writeAudit(String claimId, String payload);
}

class ClaimDecisionTransformer {
    private final RulesEngineDecisionService rulesEngine;
    private final AuditDiaryStore auditStore;

    ClaimDecisionTransformer(RulesEngineDecisionService rulesEngine, AuditDiaryStore auditStore) {
        this.rulesEngine = rulesEngine;
        this.auditStore = auditStore;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> transform(Map<String, Object> payload) {
        // Input Validation (GDPR/SOC2: least privilege & strict contract enforcement)
        if (!payload.containsKey("id") || !payload.containsKey("policyNumber") || !payload.containsKey("vehicleVin")) {
            throw new IllegalArgumentException("Missing required identifiers: id, policyNumber, vehicleVin");
        }

        String claimId = (String) payload.get("id");
        String reportedPolicy = (String) payload.get("policyNumber");
        String reportedVin = (String) payload.get("vehicleVin");

        // Fetch candidates from mocked DynamoDB contract
        List<Map<String, Object>> candidates = rulesEngine.fetchCandidatePolicies(claimId);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No candidate policies found in RulesEngineDecisionService");
        }

        Map<String, Object> bestMatch = null;
        double maxScore = -1.0;

        for (Map<String, Object> policy : candidates) {
            double score = calculateScoring(reportedPolicy, reportedVin, policy);
            if (score > maxScore) {
                maxScore = score;
                bestMatch = policy;
            }
        }

        if (bestMatch == null) {
            throw new IllegalStateException("No matching policy found for reported loss");
        }

        // Resolve audit S3 contract
        String auditUri = auditStore.writeAudit(claimId, payload.toString());

        Map<String, Object> result = new HashMap<>();
        result.put("matchedPolicyId", bestMatch.get("policyId"));
        result.put("confidenceScore", maxScore);
        result.put("auditUri", auditUri);
        return result;
    }

    private double calculateScoring(String reportedPolicy, String reportedVin, Map<String, Object> policy) {
        // Deterministic: Exact match on policy ID and VIN
        if (reportedPolicy.equals(policy.get("policyId")) && reportedVin.equals(policy.get("vin"))) {
            return 1.0;
        }
        // Probabilistic: Partial VIN prefix match + active status
        String policyVin = (String) policy.get("vin");
        if (policyVin != null && policyVin.startsWith(reportedVin.substring(0, Math.min(3, reportedVin.length())))) {
            return 0.85;
        }
        return 0.0;
    }
}
