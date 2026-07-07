package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimMatchingAlgorithmMockTest {

    @Mock
    private PolicyLookupService policyLookupService;
    @Mock
    private AuditDiaryStore auditDiaryStore;
    @InjectMocks
    private ClaimDecisionTransformer claimDecisionTransformer;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = Map.of(
            "id", "claim-std-001",
            "policyNumber", "POL-EXACT-TEST",
            "riskAddress", "100 Security Blvd",
            "namedInsured", "Jane Doe",
            "productForm", "DWELLING_FORM_3",
            "occupancy", "PRIMARY_RESIDENCE",
            "dateOfLoss", "2024-05-15"
        );
    }

    @Test
    void description_the_algorithm_performs_a_multi_stage_matching_process_first_it_attempts_exact_matches_on_policy_number_and_risk_address_if_no_exact_match_it_uses_a_weighted_scoring_model_based_on_named_insured_address_product_form_and_occupancy_it_validates_the_match_against_policy_status_and_date_of_loss_it_handles_multiple_matches_by_flagging_for_review() {
        // Stage 1: Exact match attempt on policy number and risk address
        // Mocks DynamoDB RulesEngineDecisionService_dynamodb contract
        when(policyLookupService.findExactMatch("POL-EXACT-TEST", "100 Security Blvd")).thenReturn(List.of());

        // Stage 2: Weighted scoring fallback based on named insured, address, product form, occupancy
        Map<String, Object> candidate1 = Map.of("policyNumber", "POL-SCORING-1", "score", 0.92, "status", "ACTIVE", "dateOfLoss", "2024-05-15");
        Map<String, Object> candidate2 = Map.of("policyNumber", "POL-SCORING-2", "score", 0.89, "status", "ACTIVE", "dateOfLoss", "2024-05-15");
        when(policyLookupService.computeWeightedScores(anyMap())).thenReturn(List.of(candidate1, candidate2));

        // Execute transformation
        Map<String, Object> result = claimDecisionTransformer.transform(claimPayload);

        // Stage 3: Validate match against policy status and date of loss
        assertEquals("ACTIVE", result.get("validatedStatus"));
        assertEquals("2024-05-15", result.get("validatedDateOfLoss"));

        // Stage 4: Handle multiple matches by flagging for review
        assertTrue((Boolean) result.get("flaggedForReview"), "Multiple high-scoring matches should trigger review flag");
        assertEquals(2, result.get("matchCount"));

        // Verify audit logging to S3 (AuditDiaryStore_s3 contract)
        verify(auditDiaryStore, times(1)).writeAuditRecord(anyString(), anyString());
    }

    // Mock interfaces representing external infra contracts
    private interface PolicyLookupService {
        List<Map<String, Object>> findExactMatch(String policyNumber, String riskAddress);
        List<Map<String, Object>> computeWeightedScores(Map<String, Object> payload);
    }

    private interface AuditDiaryStore {
        void writeAuditRecord(String bucketName, String objectKey);
    }
}
