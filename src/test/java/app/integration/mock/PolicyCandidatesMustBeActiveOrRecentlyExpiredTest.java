package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;

/**
 * Mock test class for Claim Data Standardization: validation: decision.
 * Validates that policy candidates must be active or recently expired before proceeding.
 */
class ClaimDataStandardizationValidationDecisionTest {

    // Mock infra contract: PolicyValidationService (DynamoDB)
    private Function<String, Map<String, Object>> policyStatusProvider;

    // Service under test (decision logic)
    private PolicyDecisionValidator decisionValidator;

    @BeforeEach
    void setUp() {
        // Default mock returns an active policy
        policyStatusProvider = pk -> Map.of("status", "ACTIVE", "expirationDate", "2025-12-31T23:59:59");
        decisionValidator = new PolicyDecisionValidator(policyStatusProvider);
    }

    @Test
    void policyCandidatesMustBeActiveOrRecentlyExpired() {
        // 1. Active policy must pass validation
        Map<String, Object> activePayload = Map.of("id", "claim-act-001", "policyId", "pol-100");
        assertTrue(decisionValidator.validatePolicyCandidateStatus(activePayload),
                "Active policy candidates must be accepted");

        // 2. Recently expired policy (within 30 days) must pass validation
        LocalDateTime fifteenDaysAgo = LocalDateTime.now().minusDays(15);
        policyStatusProvider = pk -> Map.of("status", "EXPIRED", "expirationDate", fifteenDaysAgo.toString());
        Map<String, Object> recentlyExpiredPayload = Map.of("id", "claim-exp-001", "policyId", "pol-101");
        assertTrue(decisionValidator.validatePolicyCandidateStatus(recentlyExpiredPayload),
                "Recently expired policy candidates (within 30 days) must be accepted");

        // 3. Long expired policy (older than 30 days) must fail validation
        LocalDateTime sixtyDaysAgo = LocalDateTime.now().minusDays(60);
        policyStatusProvider = pk -> Map.of("status", "EXPIRED", "expirationDate", sixtyDaysAgo.toString());
        Map<String, Object> longExpiredPayload = Map.of("id", "claim-exp-002", "policyId", "pol-102");
        assertFalse(decisionValidator.validatePolicyCandidateStatus(longExpiredPayload),
                "Long expired policy candidates must be rejected");

        // 4. Cancelled/Inactive policy must fail validation
        policyStatusProvider = pk -> Map.of("status", "CANCELLED", "expirationDate", "2024-01-01T00:00:00");
        Map<String, Object> cancelledPayload = Map.of("id", "claim-can-001", "policyId", "pol-103");
        assertFalse(decisionValidator.validatePolicyCandidateStatus(cancelledPayload),
                "Cancelled policy candidates must be rejected");
    }

    /**
     * Decision validator that enforces policy status rules.
     * Thread-safe, stateless, and relies solely on injected mock I/O contracts.
     */
    static class PolicyDecisionValidator {
        private final Function<String, Map<String, Object>> policyStatusProvider;

        PolicyDecisionValidator(Function<String, Map<String, Object>> policyStatusProvider) {
            this.policyStatusProvider = policyStatusProvider;
        }

        boolean validatePolicyCandidateStatus(Map<String, Object> payload) {
            String policyId = (String) payload.get("policyId");
            if (policyId == null || policyId.isBlank()) {
                throw new IllegalArgumentException("policyId is required in payload");
            }

            Map<String, Object> policyData = policyStatusProvider.apply(policyId);
            String status = (String) policyData.get("status");
            String expirationDateStr = (String) policyData.get("expirationDate");

            if (status == null || expirationDateStr == null) {
                throw new IllegalStateException("Policy status or expirationDate missing in infra response");
            }

            boolean isActive = "ACTIVE".equalsIgnoreCase(status);
            LocalDateTime expirationDate = LocalDateTime.parse(expirationDateStr);
            LocalDateTime now = LocalDateTime.now();
            boolean isRecentlyExpired = "EXPIRED".equalsIgnoreCase(status) &&
                    java.time.Duration.between(expirationDate, now).toDays() <= 30;

            return isActive || isRecentlyExpired;
        }
    }
}
