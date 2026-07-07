package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private RedisCacheMock redisCache;

    @Mock
    private DynamoDbMock dynamoDb;

    private ClaimRoutingDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ClaimRoutingDecisionCalculator(redisCache, dynamoDb);
    }

    @Test
    void date_of_loss_must_be_valid_iso_8601_and_not_in_future() {
        // Given: A valid payload containing a past ISO-8601 date
        String validPastIsoDate = "2023-11-05T14:30:00Z";
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateOfLoss", validPastIsoDate);

        // When: Validation and routing calculation are executed
        Map<String, Object> result = calculator.validateAndCalculate(payload);

        // Then: Validation succeeds, returns expected routing status, and no exceptions are thrown
        assertNotNull(result);
        assertEquals("VALID", result.get("routingStatus"));
        assertEquals(validPastIsoDate, result.get("dateOfLoss"));
    }

    @Test
    void date_of_loss_future_date_should_throw_validation_exception() {
        // Given: A future date
        String futureDate = LocalDate.now(ZoneId.systemDefault()).plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateOfLoss", futureDate);

        // When & Then: Must reject future dates
        assertThrows(IllegalArgumentException.class, () -> calculator.validateAndCalculate(payload));
    }

    @Test
    void date_of_loss_invalid_iso8601_format_should_throw_validation_exception() {
        // Given: Malformed date string
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateOfLoss", "2023/13/45");

        // When & Then: Must reject invalid ISO-8601 formats
        assertThrows(IllegalArgumentException.class, () -> calculator.validateAndCalculate(payload));
    }
}

// Mocked infrastructure contracts per NFR & infra_io_contracts
class RedisCacheMock {
    public String get(String cacheKeyNamespace) { return null; }
    public void put(String cacheKeyNamespace, String value, int ttlSeconds) {}
}

class DynamoDbMock {
    public Map<String, Object> getItem(String tableName, String partitionKey) { return new HashMap<>(); }
    public void putItem(String tableName, Map<String, Object> itemPayload) {}
}

// Business logic layer for Claim Initiation & Routing:decision:calculation
class ClaimRoutingDecisionCalculator {
    private final RedisCacheMock redisCache;
    private final DynamoDbMock dynamoDb;

    ClaimRoutingDecisionCalculator(RedisCacheMock redisCache, DynamoDbMock dynamoDb) {
        this.redisCache = redisCache;
        this.dynamoDb = dynamoDb;
    }

    Map<String, Object> validateAndCalculate(Map<String, Object> payload) {
        Object dateObj = payload.get("dateOfLoss");
        if (dateObj == null) {
            throw new IllegalArgumentException("dateOfLoss is required in payload");
        }

        String dateStr = dateObj.toString().trim();
        LocalDate date;
        try {
            // Strict ISO-8601 validation
            date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new IllegalArgumentException("dateOfLoss must be a valid ISO-8601 date", e);
        }

        // Business rule: Loss date cannot be in the future
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Date of loss cannot be in the future");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("routingStatus", "VALID");
        result.put("dateOfLoss", dateStr);
        result.put("calculatedAt", java.time.Instant.now());
        return result;
    }
}
