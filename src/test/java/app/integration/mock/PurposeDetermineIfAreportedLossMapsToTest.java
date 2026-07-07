package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private CacheService cacheService;

    @Mock
    private ClaimsDataStore claimsDataStore;

    private ClaimDecisionCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new ClaimDecisionCalculationService(policyLookupService, cacheService, claimsDataStore);
    }

    @Test
    void purpose_determine_if_a_reported_loss_maps_to_an_active_policy_and_validate_coverage_period() {
        // Arrange
        String claimId = "claim-init-001";
        String policyId = "pol-active-789";
        LocalDate lossDate = LocalDate.of(2024, 6, 10);

        Map<String, Object> payload = Map.of(
            "claimId", claimId,
            "policyId", policyId,
            "lossDate", lossDate.toString()
        );

        Map<String, Object> policyRecord = Map.of(
            "policyId", policyId,
            "status", "ACTIVE",
            "startDate", LocalDate.of(2024, 1, 1).toString(),
            "endDate", LocalDate.of(2024, 12, 31).toString()
        );

        // Mock external I/O: Cache & Reference Data (Redis)
        when(cacheService.get(anyString())).thenReturn(policyId);

        // Mock external I/O: Claims & Policy Data Store (DynamoDB)
        when(policyLookupService.findByPolicyId(anyString())).thenReturn(policyRecord);

        // Act
        Map<String, Object> decision = calculationService.processClaimInitiation(claimId, payload);

        // Assert: Verify mapping to active policy and coverage period validation
        assertNotNull(decision);
        assertEquals("ACTIVE", decision.get("policyStatus"));
        assertTrue((boolean) decision.get("isMappedToActivePolicy"));
        assertTrue((boolean) decision.get("isCoveragePeriodValid"));
        assertEquals(lossDate, decision.get("lossDate"));

        // Verify external calls were made exactly once (thread-safe mock behavior)
        verify(cacheService, times(1)).get(anyString());
        verify(policyLookupService, times(1)).findByPolicyId(eq(policyId));
    }

    // Minimal service interfaces representing infra contracts
    private interface PolicyLookupService {
        Map<String, Object> findByPolicyId(String policyId);
    }

    private interface CacheService {
        String get(String key);
    }

    private interface ClaimsDataStore {
        void putItem(String tableName, Map<String, Object> item);
    }

    // Simplified service under test implementing core calculation logic
    private static class ClaimDecisionCalculationService {
        private final PolicyLookupService policyLookupService;
        private final CacheService cacheService;
        private final ClaimsDataStore claimsDataStore;

        ClaimDecisionCalculationService(PolicyLookupService policyLookupService, CacheService cacheService, ClaimsDataStore claimsDataStore) {
            this.policyLookupService = policyLookupService;
            this.cacheService = cacheService;
            this.claimsDataStore = claimsDataStore;
        }

        Map<String, Object> processClaimInitiation(String claimId, Map<String, Object> payload) {
            String policyId = (String) payload.get("policyId");
            String lossDateStr = (String) payload.get("lossDate");
            
            // Input validation (NFR: input_validation)
            if (policyId == null || lossDateStr == null) {
                throw new IllegalArgumentException("Missing required payload fields: policyId, lossDate");
            }

            LocalDate lossDate = LocalDate.parse(lossDateStr);

            // Simulate cache lookup (NFR: observability/structured_logging placeholder)
            cacheService.get("Cache & Reference Data:cache:" + policyId);

            // Simulate policy data retrieval
            Map<String, Object> policyData = policyLookupService.findByPolicyId(policyId);
            String status = (String) policyData.get("status");
            LocalDate startDate = LocalDate.parse((String) policyData.get("startDate"));
            LocalDate endDate = LocalDate.parse((String) policyData.get("endDate"));

            boolean isActive = "ACTIVE".equals(status);
            boolean isValidCoverage = !lossDate.isBefore(startDate) && !lossDate.isAfter(endDate);

            Map<String, Object> result = Map.of(
                "id", claimId,
                "payload", payload,
                "policyStatus", status,
                "isMappedToActivePolicy", isActive,
                "isCoveragePeriodValid", isValidCoverage,
                "lossDate", lossDate
            );

            // Simulate DynamoDB write for audit/validation (NFR: compliance/soc2)
            claimsDataStore.putItem("Claims & Policy Data Store_table", result);

            return result;
        }
    }
}
