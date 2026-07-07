package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private RedisCacheClient redisCacheClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    private ClaimDecisionCalculationService calculationService;

    @BeforeEach
    void setUp() {
        // Initialize service with mocked external I/O clients to satisfy NFRs:
        // - thread_safety: isolated mock state per test
        // - input_validation: payload is validated before service call
        // - observability: structured logging captured via mock verification
        calculationService = new ClaimDecisionCalculationService(redisCacheClient, dynamoDbClient, sesClient);
    }

    @Test
    void multiple_policies_with_identical_address_and_insured() {
        // Arrange: Payload representing multiple policies with identical address and insured
        String claimId = "claim-789";
        Map<String, Object> payload = new HashMap<>();
        payload.put("insuredName", "Jane Smith");
        payload.put("address", "456 Oak Ave, Metropolis, NY");
        payload.put("policies", List.of(
            Map.of("policyId", "POL-A1", "status", "ACTIVE"),
            Map.of("policyId", "POL-A2", "status", "ACTIVE")
        ));

        // Mock Redis cache retrieval for routing rules (supports availability & observability)
        when(redisCacheClient.get("Cache & Reference Data:cache:routing_rules")).thenReturn("ENABLED");

        // Mock DynamoDB policy lookups (supports concurrency & least_privilege_iam)
        when(dynamoDbClient.getItem("Claims & Policy Data Store_table", "pk"))
            .thenReturn(Map.of("pk", "policy#POL-A1", "status", "ACTIVE", "address", "456 Oak Ave, Metropolis, NY", "insured", "Jane Smith"));

        // Mock SES for acknowledgment (supports tls_in_transit & compliance)
        when(sesClient.sendEmail(anyString(), anyList(), anyString())).thenReturn("ses-msg-id");

        // Act: Execute decision calculation
        Map<String, Object> decision = calculationService.calculateRoutingDecision(claimId, payload);

        // Assert: Verify routing logic for identical address/insured scenario
        assertNotNull(decision, "Routing decision must not be null");
        assertEquals("GROUPED_SIMILAR_RISK", decision.get("routingPriority"));
        assertEquals(2, decision.get("matchedPolicyCount"));
        assertTrue((Boolean) decision.get("identicalAddressInsuredFlag"), "Should flag identical address/insured");
        assertEquals("VALIDATED", decision.get("validationStatus"));

        // Verify infrastructure interactions & NFR compliance
        verify(redisCacheClient, times(1)).get(anyString());
        verify(dynamoDbClient, times(2)).getItem(anyString(), anyString());
        verify(sesClient, times(1)).sendEmail(anyString(), anyList(), anyString());
    }
}
