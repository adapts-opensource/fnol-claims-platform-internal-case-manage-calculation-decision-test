package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Infrastructure contracts (mocked to prevent live AWS/HTTP calls)
interface RedisClient {
    String get(String key);
}

interface DynamoDbClient {
    Map<String, Object> getItem(String tableName, String partitionKey);
}

interface SesClient {
    String sendEmail(String fromAddress, List<String> toAddresses, String region);
}

// Service under test for Claim Initiation & Routing:orchestration:decision
class ClaimRoutingDecisionService {
    private final RedisClient redisClient;
    private final DynamoDbClient dynamoDbClient;
    private final SesClient sesClient;

    ClaimRoutingDecisionService(RedisClient redisClient, DynamoDbClient dynamoDbClient, SesClient sesClient) {
        this.redisClient = redisClient;
        this.dynamoDbClient = dynamoDbClient;
        this.sesClient = sesClient;
    }

    String processClaimInitiation(Map<String, Object> payload) {
        String claimId = (String) payload.get("id");
        String dolStr = (String) payload.get("dateOfLoss");
        String lossTz = (String) payload.get("lossRegionTimezone");
        String initTz = (String) payload.get("initiatorRegionTimezone");

        // Normalize DOL to UTC to resolve timezone differences across regions
        LocalDateTime localDol = LocalDateTime.parse(dolStr);
        ZoneId lossZone = ZoneId.of(lossTz);
        ZoneId initZone = ZoneId.of(initTz);
        ZonedDateTime lossZoned = localDol.atZone(lossZone);
        ZonedDateTime normalizedDol = lossZoned.withZoneSameInstant(ZoneId.of("UTC"));

        // Validate normalized DOL is within business hours for routing
        int hourUtc = normalizedDol.getHour();
        if (hourUtc < 8 || hourUtc >= 18) {
            return "OUT_OF_HOURS_ROUTING";
        }

        // Check cache for routing rule
        String cachedRule = redisClient.get("Cache & Reference Data:cache:reference:routing");
        if (cachedRule != null) {
            return cachedRule;
        }

        // Fallback to DynamoDB reference data
        Map<String, Object> dbItem = dynamoDbClient.getItem("Claims & Policy Data Store_table", "pk:" + claimId);
        String tier = (String) dbItem.getOrDefault("routingTier", "STANDARD");

        // Send acknowledgment via SES
        sesClient.sendEmail("claims@newco.com", List.of("handler@newco.com"), "us-east-1");
        return tier;
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    private ClaimRoutingDecisionService routingDecisionService;

    @BeforeEach
    void setUp() {
        routingDecisionService = new ClaimRoutingDecisionService(redisClient, dynamoDbClient, sesClient);
    }

    @Test
    void timezone_differences_in_dol() {
        // Arrange
        String claimId = "CLM-TZ-001";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "dateOfLoss", "2023-11-20T08:30:00",
            "lossRegionTimezone", "America/Los_Angeles",
            "initiatorRegionTimezone", "Asia/Tokyo"
        );

        // Mock external I/O contracts
        when(redisClient.get(anyString())).thenReturn(null);
        when(dynamoDbClient.getItem(anyString(), anyString())).thenReturn(Map.of("routingTier", "PRIORITY_2"));
        when(sesClient.sendEmail(anyString(), anyList(), anyString())).thenReturn("SES-MID-99");

        // Act
        String decisionOutcome = routingDecisionService.processClaimInitiation(payload);

        // Assert
        assertNotNull(decisionOutcome);
        assertEquals("PRIORITY_2", decisionOutcome);

        // Verify infrastructure I/O contracts were invoked per spec
        verify(redisClient).get(eq("Cache & Reference Data:cache:reference:routing"));
        verify(dynamoDbClient).getItem(eq("Claims & Policy Data Store_table"), eq("pk:" + claimId));
        verify(sesClient).sendEmail(eq("claims@newco.com"), anyList(), eq("us-east-1"));
    }
}
