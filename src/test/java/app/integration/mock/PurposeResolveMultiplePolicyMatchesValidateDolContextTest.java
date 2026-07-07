package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Claim Data Standardization: validation: decision.
 * Verifies policy match resolution, DOL context validation, coverage applicability,
 * and triage routing finalization.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @InjectMocks
    private ClaimDataStandardizationService claimStandardizationService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> decisionResultCaptor;

    private static final String TEST_CLAIM_ID = "claim-std-001";
    private static final String TEST_CLAIMANT_ID = "clmnt-999";
    private static final LocalDate DATE_OF_LOSS = LocalDate.of(2023, 8, 15);
    private static final String VALID_POLICY_ID = "pol-valid-001";
    private static final String EXPIRED_POLICY_ID = "pol-expired-002";
    private static final String FUTURE_POLICY_ID = "pol-future-003";
    private static final String EXPECTED_ROUTING = "TRIAGE_ROUTING_STANDARD";

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = Map.of(
            "id", TEST_CLAIM_ID,
            "claimantId", TEST_CLAIMANT_ID,
            "dateOfLoss", DATE_OF_LOSS.toString(),
            "vehicleId", "veh-abc-123",
            "incidentType", "COLLISION"
        );
    }

    @Test
    @DisplayName("PurposeResolveMultiplePolicyMatchesValidateDolContext: Confirm coverage applicability and finalize triage routing")
    void purpose_resolve_multiple_policy_matches_validate_dol_context_confirm_coverage_applicability_and_finalize_triage_routing() {
        // Given: Multiple policy matches returned by Policy Validation Service
        List<PolicyMatch> policyMatches = List.of(
            new PolicyMatch(VALID_POLICY_ID, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), true),
            new PolicyMatch(EXPIRED_POLICY_ID, LocalDate.of(2022, 1, 1), LocalDate.of(2023, 6, 30), false),
            new PolicyMatch(FUTURE_POLICY_ID, LocalDate.of(2023, 9, 1), LocalDate.of(2024, 12, 31), false)
        );

        when(policyValidationService.resolvePolicyMatches(eq(TEST_CLAIMANT_ID)))
            .thenReturn(policyMatches);

        // When: Decision engine processes the claim
        DecisionResult result = claimStandardizationService.processDecision(testPayload);

        // Then: Verify Policy Service was invoked with correct claimant context
        verify(policyValidationService, times(1)).resolvePolicyMatches(TEST_CLAIMANT_ID);

        // Then: Verify DOL Context validation resolved to the correct policy
        assertEquals(VALID_POLICY_ID, result.selectedPolicyId());
        assertTrue(result.coverageApplicable());
        assertEquals(DATE_OF_LOSS, result.dateOfLoss());

        // Then: Verify Triage Routing was finalized based on valid policy
        verify(rulesEngineService, times(1)).getTriageRouting(
            eq(VALID_POLICY_ID),
            anyMap()
        );
        assertEquals(EXPECTED_ROUTING, result.triageRouting());

        // Then: Verify result persisted to Document Store with standardization metadata
        verify(documentStoreService, times(1)).writeResult(
            eq("DocumentStoreService-bucket"),
            eq("claim-decisions/" + TEST_CLAIM_ID + ".json"),
            decisionResultCaptor.capture()
        );

        Map<String, Object> storedPayload = decisionResultCaptor.getValue();
        assertEquals(VALID_POLICY_ID, storedPayload.get("selectedPolicyId"));
        assertEquals(true, storedPayload.get("coverageApplicable"));
        assertEquals(EXPECTED_ROUTING, storedPayload.get("triageRouting"));
        assertEquals("claim_data_standardization_transformation_valida", storedPayload.get("entityType"));
    }

    // --- Mock Infrastructure Models ---

    record PolicyMatch(String policyId, LocalDate effectiveDate, LocalDate expirationDate, boolean isActive) {}

    record DecisionResult(
        String selectedPolicyId,
        boolean coverageApplicable,
        LocalDate dateOfLoss,
        String triageRouting,
        Map<String, Object> metadata
    ) {}

    interface PolicyValidationService {
        List<PolicyMatch> resolvePolicyMatches(String claimantId);
    }

    interface RulesEngineService {
        String getTriageRouting(String policyId, Map<String, Object> context);
    }

    interface DocumentStoreService {
        void writeResult(String bucketName, String objectKey, Map<String, Object> payload);
    }

    interface ClaimDataStandardizationService {
        DecisionResult processDecision(Map<String, Object> payload);
    }
}
