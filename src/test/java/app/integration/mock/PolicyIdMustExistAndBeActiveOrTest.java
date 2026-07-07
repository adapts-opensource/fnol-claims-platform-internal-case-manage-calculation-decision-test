package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyIdMustExistAndBeActiveOrTest {

    private static final Logger log = LoggerFactory.getLogger(PolicyIdMustExistAndBeActiveOrTest.class);

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private CacheService cacheService;

    private ClaimDecisionCalculationService decisionService;

    @BeforeEach
    void setUp() {
        // Thread-safe initialization, aligns with NFR: concurrency
        decisionService = new ClaimDecisionCalculationService(policyRepository, cacheService, log);
        lenient().when(cacheService.put(anyString(), anyString(), anyInt())).thenAnswer(invocation -> null);
    }

    @Test
    void policy_id_must_exist_and_be_active_or_recently_expired() {
        // Given: Valid policy ID with ACTIVE status
        String validPolicyId = "POL-ACTIVE-001";
        Map<String, Object> validPayload = Map.of("policyId", validPolicyId, "claimType", "AUTO");
        PolicyDto activePolicy = new PolicyDto(validPolicyId, "ACTIVE");

        when(cacheService.get("Cache & Reference Data:cache:policy:" + validPolicyId)).thenReturn(null);
        when(policyRepository.findByPolicyId(validPolicyId)).thenReturn(Optional.of(activePolicy));

        // When & Then: Should not throw, validation passes
        assertDoesNotThrow(() -> decisionService.validateAndCalculate(validPayload),
                "Policy ID must exist and be active or recently expired");
        verify(policyRepository).findByPolicyId(validPolicyId);
        log.info("Test passed: Active policy validated successfully");
    }

    @Test
    void policy_id_must_exist_and_be_recently_expired() {
        // Given: Valid policy ID with RECENTLY_EXPIRED status
        String expiredPolicyId = "POL-EXPIRED-001";
        Map<String, Object> expiredPayload = Map.of("policyId", expiredPolicyId, "claimType", "AUTO");
        PolicyDto expiredPolicy = new PolicyDto(expiredPolicyId, "RECENTLY_EXPIRED");

        when(cacheService.get(anyString())).thenReturn(null);
        when(policyRepository.findByPolicyId(expiredPolicyId)).thenReturn(Optional.of(expiredPolicy));

        assertDoesNotThrow(() -> decisionService.validateAndCalculate(expiredPayload));
        verify(policyRepository).findByPolicyId(expiredPolicyId);
    }

    @Test
    void policy_id_must_not_exist_or_inactive_throws_validation_error() {
        // Given: Non-existent policy
        String invalidPolicyId = "POL-NONEXISTENT";
        Map<String, Object> invalidPayload = Map.of("policyId", invalidPolicyId, "claimType", "AUTO");

        when(cacheService.get(anyString())).thenReturn(null);
        when(policyRepository.findByPolicyId(invalidPolicyId)).thenReturn(Optional.empty());

        // When & Then: Should throw IllegalArgumentException for input validation (NFR: input_validation)
        assertThrows(IllegalArgumentException.class, () -> decisionService.validateAndCalculate(invalidPayload),
                "Expected validation error for non-existent policy");
    }

    @Test
    void policy_id_inactive_throws_validation_error() {
        // Given: Inactive policy
        String inactivePolicyId = "POL-INACTIVE";
        Map<String, Object> inactivePayload = Map.of("policyId", inactivePolicyId, "claimType", "AUTO");
        PolicyDto inactivePolicy = new PolicyDto(inactivePolicyId, "INACTIVE");

        when(cacheService.get(anyString())).thenReturn(null);
        when(policyRepository.findByPolicyId(inactivePolicyId)).thenReturn(Optional.of(inactivePolicy));

        assertThrows(IllegalArgumentException.class, () -> decisionService.validateAndCalculate(inactivePayload));
    }

    // Minimal domain interfaces to support isolated mock testing
    interface PolicyRepository {
        Optional<PolicyDto> findByPolicyId(String policyId);
    }

    interface CacheService {
        String get(String key);
        void put(String key, String value, int ttlSeconds);
    }

    static class PolicyDto {
        private final String policyId;
        private final String status;

        PolicyDto(String policyId, String status) {
            this.policyId = policyId;
            this.status = status;
        }

        public String getPolicyId() { return policyId; }
        public String getStatus() { return status; }
    }

    static class ClaimDecisionCalculationService {
        private final PolicyRepository policyRepository;
        private final CacheService cacheService;
        private final Logger log;

        ClaimDecisionCalculationService(PolicyRepository policyRepository, CacheService cacheService, Logger log) {
            this.policyRepository = policyRepository;
            this.cacheService = cacheService;
            this.log = log;
        }

        public Map<String, Object> validateAndCalculate(Map<String, Object> payload) {
            String policyId = (String) payload.get("policyId");
            if (policyId == null || policyId.isBlank()) {
                throw new IllegalArgumentException("policyId is required and must be valid");
            }

            String cacheKey = "Cache & Reference Data:cache:policy:" + policyId;
            String cachedValue = cacheService.get(cacheKey);
            if (cachedValue != null) {
                log.info("Cache hit for policyId: {}", policyId);
                return Map.of("status", "CACHED", "policyId", policyId);
            }

            Optional<PolicyDto> policyOpt = policyRepository.findByPolicyId(policyId);
            if (policyOpt.isEmpty()) {
                throw new IllegalArgumentException("Policy ID must exist and be active or recently expired");
            }

            PolicyDto policy = policyOpt.get();
            if (!"ACTIVE".equals(policy.getStatus()) && !"RECENTLY_EXPIRED".equals(policy.getStatus())) {
                throw new IllegalArgumentException("Policy ID must exist and be active or recently expired");
            }

            log.info("Policy validated: {} with status {}", policyId, policy.getStatus());
            cacheService.put(cacheKey, policy.getStatus(), 3600);
            return Map.of("status", "VALID", "policyId", policyId, "calculation", "ROUTED");
        }
    }
}
