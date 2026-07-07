package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private DocumentStoreService documentStoreService;

    private ClaimDecisionEngine claimDecisionEngine;

    private static final String CLAIM_ID = "claim-123";
    private static final String POLICY_ID = "policy-456";
    private static final String BUCKET_NAME = "DocumentStoreService-bucket";
    private static final String OBJECT_KEY_PATTERN = "DocumentStoreService/{entity_id}.json";
    private static final String RESOLVED_OBJECT_KEY = "DocumentStoreService/claim-123.json";

    @BeforeEach
    void setUp() {
        claimDecisionEngine = new ClaimDecisionEngine(rulesEngineService, policyValidationService, documentStoreService);
    }

    @Test
    void description_analyst_reviews_match_candidates_selects_correct_policy_validates_dol_against_policy_period_restrictions_confirms_coverage_and_updates_claim_status_with_explainability() {
        // Given: Analyst reviews match candidates
        List<String> matchCandidates = Arrays.asList(POLICY_ID, "policy-789");
        when(rulesEngineService.getMatchCandidates(CLAIM_ID)).thenReturn(matchCandidates);

        // Given: Policy details with period & restrictions
        Map<String, Object> policyDetails = new HashMap<>();
        policyDetails.put("periodStart", "2023-01-01");
        policyDetails.put("periodEnd", "2023-12-31");
        policyDetails.put("restrictions", Arrays.asList("DOL_AFTER_ISSUE_DATE", "NO_FRAUD_HISTORY"));
        when(policyValidationService.getPolicyDetails(POLICY_ID)).thenReturn(policyDetails);

        // Given: Claim data standardization payload
        String dol = "2023-06-15";
        Map<String, Object> initialPayload = new HashMap<>();
        initialPayload.put("id", CLAIM_ID);
        initialPayload.put("payload", Map.of("dateOfLoss", dol, "selectedPolicy", POLICY_ID));

        // Given: Document store mock
        String expectedUri = "s3://" + BUCKET_NAME + "/" + RESOLVED_OBJECT_KEY;
        when(documentStoreService.storeClaimData(eq(BUCKET_NAME), eq(RESOLVED_OBJECT_KEY), any(Map.class)))
                .thenReturn(expectedUri);

        // When: Engine processes validation & decision
        String resultUri = claimDecisionEngine.processDecision(
                CLAIM_ID, POLICY_ID, dol, BUCKET_NAME, RESOLVED_OBJECT_KEY, initialPayload);

        // Then: Verify interactions
        verify(rulesEngineService).getMatchCandidates(CLAIM_ID);
        verify(policyValidationService).getPolicyDetails(POLICY_ID);
        verify(documentStoreService).storeClaimData(eq(BUCKET_NAME), eq(RESOLVED_OBJECT_KEY), any(Map.class));

        // Then: Assert final state & explainability
        assertEquals(expectedUri, resultUri);

        ArgumentCaptor<Map<String, Object>> updatedPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(documentStoreService).storeClaimData(eq(BUCKET_NAME), eq(RESOLVED_OBJECT_KEY), updatedPayloadCaptor.capture());
        Map<String, Object> capturedPayload = updatedPayloadCaptor.getValue();

        assertEquals("APPROVED", capturedPayload.get("claimStatus"));
        assertEquals("DOL within policy period and restrictions met", capturedPayload.get("explainability"));
        assertEquals(CLAIM_ID, capturedPayload.get("id"));
        assertNotNull(capturedPayload.get("payload"));
    }

    // Mock Infrastructure Contracts
    interface RulesEngineService {
        List<String> getMatchCandidates(String claimId);
    }

    interface PolicyValidationService {
        Map<String, Object> getPolicyDetails(String policyId);
    }

    interface DocumentStoreService {
        String storeClaimData(String bucketName, String objectKey, Map<String, Object> payload);
    }

    // Service Under Test
    static class ClaimDecisionEngine {
        private final RulesEngineService rulesEngineService;
        private final PolicyValidationService policyValidationService;
        private final DocumentStoreService documentStoreService;

        ClaimDecisionEngine(RulesEngineService rulesEngineService,
                            PolicyValidationService policyValidationService,
                            DocumentStoreService documentStoreService) {
            this.rulesEngineService = rulesEngineService;
            this.policyValidationService = policyValidationService;
            this.documentStoreService = documentStoreService;
        }

        String processDecision(String claimId, String selectedPolicyId, String dol,
                               String bucketName, String objectKey, Map<String, Object> initialPayload) {
            // 1. Review match candidates
            List<String> candidates = rulesEngineService.getMatchCandidates(claimId);
            assertTrue(candidates.contains(selectedPolicyId), "Selected policy must be in match candidates");

            // 2. Validate DOL against policy period & restrictions
            Map<String, Object> policyDetails = policyValidationService.getPolicyDetails(selectedPolicyId);
            LocalDate periodStart = LocalDate.parse((String) policyDetails.get("periodStart"));
            LocalDate periodEnd = LocalDate.parse((String) policyDetails.get("periodEnd"));
            LocalDate lossDate = LocalDate.parse(dol);
            assertTrue(lossDate.isAfter(periodStart) && lossDate.isBefore(periodEnd),
                    "Date of Loss must fall within policy period");

            // 3. Confirm coverage & apply restrictions
            List<String> restrictions = (List<String>) policyDetails.get("restrictions");
            assertFalse(restrictions.contains("DOL_BEFORE_ISSUE_DATE"), "Restriction violation");

            // 4. Update claim status with explainability
            Map<String, Object> updatedPayload = new HashMap<>(initialPayload);
            updatedPayload.put("claimStatus", "APPROVED");
            updatedPayload.put("explainability", "DOL within policy period and restrictions met");
            updatedPayload.put("validatedAt", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

            // 5. Persist to Document Store (S3 contract)
            return documentStoreService.storeClaimData(bucketName, objectKey, updatedPayload);
        }
    }
}
