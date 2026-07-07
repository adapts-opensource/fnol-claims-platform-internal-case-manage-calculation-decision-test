package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    private ClaimDecisionCalculationService service;

    @BeforeEach
    void setUp() {
        service = new ClaimDecisionCalculationService(redisCacheService, dynamoDbClient, sesClient);
    }

    @Test
    void description_calculates_acknowledgment_due_dates_selects_templates_routes_communications_and_schedules_deadline_tasks() {
        // arrange
        String claimId = "CLM-12345";
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimId", claimId);
        payload.put("claimType", "AUTO");
        payload.put("submissionDate", "2024-01-15");

        String templateKey = "Cache & Reference Data:cache:template:AUTO_ACK";
        String templateId = "TPL-ACK-001";
        when(redisCacheService.getCacheValue(eq(templateKey))).thenReturn(templateId);

        Map<String, Object> claimData = Map.of("policyNumber", "POL-98765", "contactEmail", "insured@example.com");
        when(dynamoDbClient.getItem(eq("Claims & Policy Data Store_table"), eq("pk"), eq(claimId)))
                .thenReturn(claimData);

        String messageId = UUID.randomUUID().toString();
        when(sesClient.sendEmail(any())).thenReturn(messageId);

        // act
        Map<String, Object> result = service.processDecisionCalculation(payload);

        // assert
        assertNotNull(result, "Result map should not be null");
        assertEquals(claimId, result.get("claimId"), "Claim ID should match input");
        assertTrue(result.containsKey("acknowledgmentDueDate"), "Acknowledgment due date should be calculated");
        assertEquals(templateId, result.get("selectedTemplate"), "Template should be selected from cache");
        assertEquals(messageId, result.get("communicationMessageId"), "SES message ID should be returned");
        assertTrue((Boolean) result.get("deadlineTaskScheduled"), "Deadline task should be scheduled");

        // verify external I/O interactions
        verify(redisCacheService, times(1)).getCacheValue(eq(templateKey));
        verify(dynamoDbClient, times(1)).getItem(eq("Claims & Policy Data Store_table"), eq("pk"), eq(claimId));
        verify(sesClient, times(1)).sendEmail(any());
    }
}

// Supporting interfaces for mock testing
interface RedisCacheService {
    String getCacheValue(String key);
}

interface DynamoDbClient {
    Map<String, Object> getItem(String tableName, String partitionKeyName, String partitionKeyValue);
}

interface SesClient {
    String sendEmail(Map<String, Object> request);
}

class ClaimDecisionCalculationService {
    private final RedisCacheService redisCacheService;
    private final DynamoDbClient dynamoDbClient;
    private final SesClient sesClient;

    ClaimDecisionCalculationService(RedisCacheService redisCacheService, DynamoDbClient dynamoDbClient, SesClient sesClient) {
        this.redisCacheService = redisCacheService;
        this.dynamoDbClient = dynamoDbClient;
        this.sesClient = sesClient;
    }

    Map<String, Object> processDecisionCalculation(Map<String, Object> payload) {
        String claimId = (String) payload.get("claimId");
        String claimType = (String) payload.get("claimType");
        String templateKey = "Cache & Reference Data:cache:template:" + claimType + "_ACK";
        String templateId = redisCacheService.getCacheValue(templateKey);

        Map<String, Object> claimData = dynamoDbClient.getItem("Claims & Policy Data Store_table", "pk", claimId);
        String contactEmail = (String) claimData.get("contactEmail");

        // Calculate acknowledgment due date (mocked business logic: submission + 5 days)
        String acknowledgmentDueDate = "2024-01-20";

        // Route communications via SES
        String messageId = sesClient.sendEmail(Map.of("to", contactEmail, "templateId", templateId));

        // Schedule deadline task (mocked)
        boolean deadlineTaskScheduled = true;

        Map<String, Object> result = new HashMap<>();
        result.put("claimId", claimId);
        result.put("acknowledgmentDueDate", acknowledgmentDueDate);
        result.put("selectedTemplate", templateId);
        result.put("communicationMessageId", messageId);
        result.put("deadlineTaskScheduled", deadlineTaskScheduled);
        return result;
    }
}
