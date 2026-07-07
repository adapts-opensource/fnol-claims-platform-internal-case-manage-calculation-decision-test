package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationOrchestrationTransformationTest {

    @Mock
    private ComplianceAuditStorage complianceAuditStorage;
    @Mock
    private PolicyClaimsRepository policyClaimsRepository;

    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrationService = new ClaimOrchestrationService(complianceAuditStorage, policyClaimsRepository);
    }

    @Test
    void inputCriteriaRequiredCandidatePoliciesMatchScoresPolicyPeriodsOccupancyDetailsOptionalHistoricalClaimDataEndorsementHistoryValidationCandidatesMustBeActiveOrRecentlyExpiredScoresMustBeNormalized0To100FreshnessRequirementsPolicyDataMustBeCurrentWithin1HourTest() {
        // Arrange valid input payload matching feature contract
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("candidate_policies", List.of(createActivePolicy()));
        inputPayload.put("match_scores", List.of(85.0));
        inputPayload.put("policy_periods", List.of("2024-01-01/2024-12-31"));
        inputPayload.put("occupancy_details", Map.of("occupancy_type", "PrimaryResidence"));
        inputPayload.put("historical_claim_data", List.of());
        inputPayload.put("endorsement_history", List.of());

        // Mock external I/O (S3 & DynamoDB abstractions)
        when(complianceAuditStorage.storeAuditLog(anyString(), anyString())).thenReturn("s3://ComplianceAuditService-bucket/ComplianceAuditService/claim.json");
        when(policyClaimsRepository.saveItem(anyMap())).thenReturn(Map.of("pk", "claim-123", "sk", "claim-123"));

        // Act
        Map<String, Object> transformationResult = orchestrationService.transformAndValidate(inputPayload);

        // Assert orchestration & validation outcomes
        assertNotNull(transformationResult, "Transformation result must not be null");
        assertTrue(transformationResult.containsKey("validated_claims"), "Result must contain validated_claims");
        assertEquals(1, ((List<?>) transformationResult.get("validated_claims")).size(), "Should process exactly one candidate");
        
        // Verify external I/O was invoked exactly once with expected parameters
        verify(complianceAuditStorage, times(1)).storeAuditLog(eq("ComplianceAuditService"), anyString());
        verify(policyClaimsRepository, times(1)).saveItem(anyMap());
    }

    private Map<String, Object> createActivePolicy() {
        Map<String, Object> policy = new HashMap<>();
        policy.put("policy_id", "POL-12345");
        policy.put("status", "ACTIVE");
        policy.put("last_updated", LocalDateTime.now().minusMinutes(30)); // Satisfies < 1 hour freshness
        return policy;
    }

    // Minimal orchestration service under test
    private static class ClaimOrchestrationService {
        private final ComplianceAuditStorage auditStorage;
        private final PolicyClaimsRepository claimsRepo;

        ClaimOrchestrationService(ComplianceAuditStorage auditStorage, PolicyClaimsRepository claimsRepo) {
            this.auditStorage = auditStorage;
            this.claimsRepo = claimsRepo;
        }

        Map<String, Object> transformAndValidate(Map<String, Object> input) {
            Map<String, Object> result = new HashMap<>();
            List<String> requiredFields = List.of("candidate_policies", "match_scores", "policy_periods", "occupancy_details");
            
            // Input validation: required fields
            for (String field : requiredFields) {
                if (!input.containsKey(field)) {
                    throw new IllegalArgumentException("Missing required field: " + field);
                }
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> policies = (List<Map<String, Object>>) input.get("candidate_policies");
            @SuppressWarnings("unchecked")
            List<Double> scores = (List<Double>) input.get("match_scores");

            // Validation: candidates must be active or recently expired
            for (Map<String, Object> pol : policies) {
                String status = (String) pol.get("status");
                if (!"ACTIVE".equals(status) && !"RECENTLY_EXPIRED".equals(status)) {
                    throw new IllegalArgumentException("Candidates must be active or recently expired");
                }
                LocalDateTime updated = (LocalDateTime) pol.get("last_updated");
                if (updated == null || updated.isBefore(LocalDateTime.now().minusHours(1))) {
                    throw new IllegalArgumentException("Policy data must be current within 1 hour");
                }
            }

            // Validation: scores must be normalized 0-100
            for (Double score : scores) {
                if (score < 0.0 || score > 100.0) {
                    throw new IllegalArgumentException("Scores must be normalized 0-100");
                }
            }

            // Persist transformed data to external I/O (mocked)
            auditStorage.storeAuditLog("ComplianceAuditService", "claim_transformation.json");
            claimsRepo.saveItem(Map.of("pk", "claim-123", "sk", "claim-123", "data", policies));

            result.put("validated_claims", policies);
            return result;
        }
    }

    interface ComplianceAuditStorage {
        String storeAuditLog(String bucket, String key);
    }

    interface PolicyClaimsRepository {
        Map<String, Object> saveItem(Map<String, Object> item);
    }
}
