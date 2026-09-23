package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimRoutingDecisionValidationMockTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private EmailService emailService;

    private ClaimRoutingDecisionValidationService decisionValidator;
    private static final int DEFAULT_GRACE_PERIOD_DAYS = 30;

    @BeforeEach
    void setUp() {
        decisionValidator = new ClaimRoutingDecisionValidationService(
                cacheService, dynamoDbService, emailService, DEFAULT_GRACE_PERIOD_DAYS
        );
    }

    @Test
    @DisplayName("if_expired_dol_must_be_within_grace_period_if_applicable")
    void ifExpiredDolMustBeWithinGracePeriodIfApplicable() {
        // Given: Expired claim with DOL within grace period
        Map<String, Object> payloadWithinGrace = buildPayload("EXPIRED", LocalDate.now().minusDays(10));
        when(cacheService.getGracePeriodDays()).thenReturn(DEFAULT_GRACE_PERIOD_DAYS);

        // When & Then: Should not throw
        assertDoesNotThrow(() -> decisionValidator.validate(payloadWithinGrace),
                "Claim should be valid when expired but DOL is within grace period");

        // Given: Expired claim with DOL outside grace period
        Map<String, Object> payloadOutsideGrace = buildPayload("EXPIRED", LocalDate.now().minusDays(45));
        when(cacheService.getGracePeriodDays()).thenReturn(DEFAULT_GRACE_PERIOD_DAYS);

        // When & Then: Should throw validation exception
        assertThrows(IllegalArgumentException.class, () -> decisionValidator.validate(payloadOutsideGrace),
                "Claim should be invalid when expired and DOL exceeds grace period");

        // Given: Non-expired claim (grace period check is skipped)
        Map<String, Object> payloadActive = buildPayload("ACTIVE", LocalDate.now().minusDays(60));
        assertDoesNotThrow(() -> decisionValidator.validate(payloadActive),
                "Grace period validation should not apply to non-expired claims");
    }

    private Map<String, Object> buildPayload(String status, LocalDate dateOfLoss) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-" + System.currentTimeMillis());
        payload.put("status", status);
        payload.put("dateOfLoss", dateOfLoss.toString());
        return payload;
    }

    // Minimal interfaces matching infra contracts for compilation & mocking
    interface CacheService {
        int getGracePeriodDays();
        String getCachedValue(String key);
    }

    interface DynamoDbService {
        Map<String, Object> getItem(String tableName, String pk);
    }

    interface EmailService {
        String sendMessage(String from, String to, String region);
    }

    // Simplified orchestration/decision service under test
    static class ClaimRoutingDecisionValidationService {
        private final CacheService cacheService;
        private final DynamoDbService dynamoDbService;
        private final EmailService emailService;
        private final int gracePeriodDays;

        ClaimRoutingDecisionValidationService(CacheService cacheService, DynamoDbService dynamoDbService,
                                              EmailService emailService, int gracePeriodDays) {
            this.cacheService = cacheService;
            this.dynamoDbService = dynamoDbService;
            this.emailService = emailService;
            this.gracePeriodDays = gracePeriodDays;
        }

        void validate(Map<String, Object> payload) {
            String status = (String) payload.getOrDefault("status", "UNKNOWN");
            String dolStr = (String) payload.get("dateOfLoss");
            if (dolStr == null) {
                throw new IllegalArgumentException("DOL is required for decision validation");
            }

            LocalDate dateOfLoss = LocalDate.parse(dolStr);
            if (!"EXPIRED".equals(status)) {
                return; // Grace period only applicable if expired
            }

            long daysSinceLoss = ChronoUnit.DAYS.between(dateOfLoss, LocalDate.now());
            if (daysSinceLoss > gracePeriodDays) {
                throw new IllegalArgumentException("DOL must be within grace period if applicable");
            }
        }
    }
}
