package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private RedisCache redisCache;

    @Mock
    private DynamoDbPolicyStore policyStore;

    @Mock
    private SesNotificationService sesService;

    @Mock
    private StructuredLogger logger;

    private ClaimInitiationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Wire dependencies under test with mocked infrastructure I/O
        orchestrator = new ClaimInitiationOrchestrator(redisCache, policyStore, sesService, logger);
    }

    @Test
    void dataMismatchBetweenInputAndPas() {
        // Arrange: Valid input payload vs divergent PAS record
        String claimId = "CLM-2024-001";
        Map<String, Object> inputPayload = Map.of(
                "policyNumber", "POL-98765",
                "insuredName", "Alice Smith",
                "coverageType", "COMPREHENSIVE"
        );
        Map<String, Object> pasRecord = Map.of(
                "policyNumber", "POL-98765",
                "insuredName", "Bob Jones", // Intentional mismatch
                "coverageType", "COMPREHENSIVE"
        );

        // Mock Redis cache hit (NFR: observability & performance)
        when(redisCache.get("Cache & Reference Data:cache:policy:POL-98765"))
                .thenReturn("POL-98765");

        // Mock DynamoDB policy store lookup (NFR: compliance & data integrity)
        when(policyStore.queryItem("pk", "POL-98765"))
                .thenReturn(pasRecord);

        // Act & Assert: Expect validation failure on mismatch
        DataValidationException exception = assertThrows(DataValidationException.class, () -> {
            orchestrator.processDecision(claimId, inputPayload);
        });

        assertEquals("Data mismatch between input payload and PAS record for policy POL-98765", exception.getMessage());

        // Verify infrastructure interactions
        verify(redisCache).get("Cache & Reference Data:cache:policy:POL-98765");
        verify(policyStore).queryItem("pk", "POL-98765");
        // SES should NOT be triggered due to validation failure (NFR: least privilege & security)
        verify(sesService, never()).sendEmail(anyString(), anyList(), anyString());
    }

    // --- Minimal Infrastructure & Domain Stubs for Compilation ---
    static class ClaimInitiationOrchestrator {
        private final RedisCache redisCache;
        private final DynamoDbPolicyStore policyStore;
        private final SesNotificationService sesService;
        private final StructuredLogger logger;

        ClaimInitiationOrchestrator(RedisCache redisCache, DynamoDbPolicyStore policyStore,
                                    SesNotificationService sesService, StructuredLogger logger) {
            this.redisCache = redisCache;
            this.policyStore = policyStore;
            this.sesService = sesService;
            this.logger = logger;
        }

        void processDecision(String id, Map<String, Object> payload) {
            logger.info("Processing claim initiation decision", "claimId", id);
            if (payload == null || !payload.containsKey("policyNumber")) {
                throw new IllegalArgumentException("Input validation failed: payload missing policyNumber");
            }

            String policyNum = (String) payload.get("policyNumber");
            String cacheKey = "Cache & Reference Data:cache:policy:" + policyNum;
            String cachedRef = redisCache.get(cacheKey);
            if (cachedRef == null) {
                throw new RuntimeException("Cache miss: " + cacheKey);
            }

            Map<String, Object> pasData = policyStore.queryItem("pk", policyNum);
            if (!pasData.equals(payload)) {
                logger.warn("Data mismatch detected", "policyNumber", policyNum);
                throw new DataValidationException("Data mismatch between input payload and PAS record for policy " + policyNum);
            }

            logger.info("Routing claim to PAS", "policyNumber", policyNum);
            sesService.sendEmail("noreply@newco.com", List.of("ops@newco.com"), "us-east-1");
        }
    }

    interface RedisCache { String get(String key); }
    interface DynamoDbPolicyStore { Map<String, Object> queryItem(String partitionKey, String partitionValue); }
    interface SesNotificationService { void sendEmail(String from, List<String> to, String region); }
    interface StructuredLogger { void info(String message, String key, String value); void warn(String message, String key, String value); }
    static class DataValidationException extends RuntimeException {
        DataValidationException(String message) { super(message); }
    }
}
