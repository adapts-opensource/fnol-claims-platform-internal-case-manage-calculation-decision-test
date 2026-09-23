package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActiveCatMoratoriumFlagForComplianceReviewTest {

    @Mock
    private CacheReferenceDataService cacheService;

    @Mock
    private ClaimsDataStoreService claimsDataStoreService;

    private ClaimDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ClaimDecisionCalculator(cacheService, claimsDataStoreService);
    }

    @Test
    void active_cat_moratorium_flag_for_compliance_review() {
        // given: payload simulating claim initiation with active CAT moratorium
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-789");
        payload.put("active_cat_moratorium", true);
        payload.put("policy_type", "AUTO");

        // mock external I/O: Redis cache returns active moratorium status
        when(cacheService.getMoratoriumStatus("AUTO")).thenReturn("ACTIVE");
        // mock external I/O: DynamoDB returns claim context
        when(claimsDataStoreService.fetchClaimContext("claim-789")).thenReturn(Map.of("status", "INITIATED"));

        // when: execute decision calculation
        ClaimDecisionResult result = calculator.calculateDecision(payload);

        // then: verify compliance review flag is set per business rule
        assertNotNull(result);
        assertTrue(result.isFlagForComplianceReview(),
                "Active CAT moratorium must flag the claim for compliance review");
        assertEquals("COMPLIANCE_REVIEW", result.getRoutingDestination());

        // verify external I/O contracts were invoked correctly
        verify(cacheService).getMoratoriumStatus("AUTO");
        verify(claimsDataStoreService).fetchClaimContext("claim-789");
    }

    // Minimal domain model for compilation
    static class ClaimDecisionResult {
        private boolean flagForComplianceReview;
        private String routingDestination;

        public boolean isFlagForComplianceReview() { return flagForComplianceReview; }
        public void setFlagForComplianceReview(boolean flagForComplianceReview) { this.flagForComplianceReview = flagForComplianceReview; }
        public String getRoutingDestination() { return routingDestination; }
        public void setRoutingDestination(String routingDestination) { this.routingDestination = routingDestination; }
    }

    interface CacheReferenceDataService {
        String getMoratoriumStatus(String policyType);
    }

    interface ClaimsDataStoreService {
        Map<String, Object> fetchClaimContext(String claimId);
    }

    class ClaimDecisionCalculator {
        private final CacheReferenceDataService cacheService;
        private final ClaimsDataStoreService claimsDataStoreService;

        ClaimDecisionCalculator(CacheReferenceDataService cacheService, ClaimsDataStoreService claimsDataStoreService) {
            this.cacheService = cacheService;
            this.claimsDataStoreService = claimsDataStoreService;
        }

        public ClaimDecisionResult calculateDecision(Map<String, Object> payload) {
            ClaimDecisionResult result = new ClaimDecisionResult();
            String policyType = (String) payload.getOrDefault("policy_type", "AUTO");
            String moratoriumStatus = cacheService.getMoratoriumStatus(policyType);
            if ("ACTIVE".equals(moratoriumStatus)) {
                result.setFlagForComplianceReview(true);
                result.setRoutingDestination("COMPLIANCE_REVIEW");
            }
            return result;
        }
    }
}
