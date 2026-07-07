package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration tests for Claim Initiation & Routing:decision:validation.
 * Verifies catastrophe event tagging logic using mocked infrastructure.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationMockTest {

    @Mock
    private ClaimRoutingDecisionService claimRoutingDecisionService;

    @Mock
    private RedisService redisService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SesService sesService;

    @InjectMocks
    private ClaimInitiationService claimInitiationService;

    @Test
    void validate_catastrophe_event_tagging() {
        // Arrange: Setup inputs for CatastropheEvent test case
        Map<String, Object> payload = Map.of(
                "policy_number", "POL-CAT",
                "loss_date", "2024-05-15",
                "event_name", "Hurricane_Zelda",
                "event_code", "CAT-2024-05"
        );

        ClaimInitiationRequest request = new ClaimInitiationRequest("mock-claim-id-001", payload);

        // Expected results based on feature description
        ClaimInitiationResult expectedResult = new ClaimInitiationResult(
                "Catastrophe claim",
                "Claim Opened",
                "CAT-2024-05",
                List.of("Catastrophe Assignment")
        );

        // Mock service behavior
        when(claimRoutingDecisionService.processValidation(any(ClaimInitiationRequest.class)))
                .thenReturn(expectedResult);

        // Act: Execute claim initiation and validation
        ClaimInitiationResult result = claimInitiationService.initiateAndValidate(request);

        // Assert: Verify expected results
        assertNotNull(result, "Result should not be null");
        assertEquals("Catastrophe claim", result.claimType(), "Initial claim type should be Catastrophe claim");
        assertEquals("Claim Opened", result.status(), "Status should be set to Claim Opened");
        assertEquals("CAT-2024-05", result.linkedEventCode(), "Claim should be linked to event CAT-2024-05");
        assertTrue(result.tasks().contains("Catastrophe Assignment"), "Task Catastrophe Assignment should be created");

        // Verify Infra I/O Contracts (Mocked)
        // Redis: Cache lookup for event code validation
        verify(redisService, times(1)).getCacheEntry(eq("Cache & Reference Data:cache:CAT-2024-05"));
        
        // DynamoDB: Persist claim linked to event partition
        verify(dynamoDbService, times(1)).putItem(
                eq("Claims & Policy Data Store_table"),
                argThat(isLinkedToEvent("CAT-2024-05"))
        );
        
        // SES: No email expected in validation phase, verify no unintended calls
        verify(sesService, never()).sendEmail(any(), any(), any());
    }

    /**
     * Helper predicate to verify DynamoDB item contains correct event linkage.
     */
    private Predicate<Map<String, Object>> isLinkedToEvent(String expectedEventCode) {
        return item -> item.containsKey("pk") && item.get("pk").equals(expectedEventCode);
    }

    // --- Supporting Records for Test Context ---

    record ClaimInitiationRequest(String id, Map<String, Object> payload) {}

    record ClaimInitiationResult(String claimType, String status, String linkedEventCode, List<String> tasks) {}

    // --- Mock Interfaces for Infrastructure Contracts ---

    interface ClaimRoutingDecisionService {
        ClaimInitiationResult processValidation(ClaimInitiationRequest request);
    }

    interface RedisService {
        String getCacheEntry(String key);
    }

    interface DynamoDbService {
        void putItem(String tableName, Map<String, Object> item);
    }

    interface SesService {
        String sendEmail(String from, List<String> to, String region);
    }
}
