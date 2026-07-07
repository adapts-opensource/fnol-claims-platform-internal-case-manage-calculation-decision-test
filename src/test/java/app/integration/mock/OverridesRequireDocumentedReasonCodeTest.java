package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimRoutingDecisionCalculationMockTest {

    // Mocked infrastructure services per contract
    @Mock
    private RedisCacheService redisCache;
    @Mock
    private DynamoDbService dynamoDb;
    @Mock
    private SesCommunicationService sesService;

    @InjectMocks
    private RoutingDecisionCalculator calculator;

    // Infrastructure interfaces representing external I/O contracts
    public interface RedisCacheService {
        String get(String key);
        void put(String key, String val, int ttl);
    }

    public interface DynamoDbService {
        Map<String, Object> getItem(String table, String pk);
        void putItem(String table, Map<String, Object> item);
    }

    public interface SesCommunicationService {
        String sendMessage(String from, String[] to, String region);
    }

    // Service under test
    public static class RoutingDecisionCalculator {
        private final RedisCacheService redisCache;
        private final DynamoDbService dynamoDb;
        private final SesCommunicationService sesService;

        public RoutingDecisionCalculator(RedisCacheService redisCache, DynamoDbService dynamoDb, SesCommunicationService sesService) {
            this.redisCache = redisCache;
            this.dynamoDb = dynamoDb;
            this.sesService = sesService;
        }

        public Map<String, Object> calculateDecision(Map<String, Object> payload) {
            if (Boolean.TRUE.equals(payload.get("is_override"))) {
                String reasonCode = payload.get("reason_code") != null ? payload.get("reason_code").toString().trim() : "";
                if (reasonCode.isEmpty()) {
                    throw new IllegalArgumentException("Overrides require documented reason code");
                }
            }
            Map<String, Object> result = new HashMap<>();
            result.put("status", "CALCULATED");
            result.put("payload", payload);
            return result;
        }
    }

    @Test
    void overrides_require_documented_reason_code() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-789");
        payload.put("is_override", true);
        payload.put("reason_code", null);

        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateDecision(payload);
        });
    }

    @Test
    void overrides_with_documented_reason_code_succeeds() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-790");
        payload.put("is_override", true);
        payload.put("reason_code", "REASON_CODE_01");

        Map<String, Object> result = calculator.calculateDecision(payload);

        assertNotNull(result);
        assertEquals("CALCULATED", result.get("status"));
    }
}
