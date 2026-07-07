package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private ReferenceDataLookup referenceDataLookup;

    @Mock
    private ClaimRoutingOrchestrator routingOrchestrator;

    @InjectMocks
    private ClaimInitiationRoutingDecisionService decisionService;

    private static final String CLAIM_ID = "CLM-2024-001";
    private static final String ADDRESS_ID = "ADDR-REF-NULL";

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks; reset state if running multiple scenarios
        reset(referenceDataLookup, routingOrchestrator);
    }

    @Test
    @DisplayName("address_not_found_in_reference_data")
    void address_not_found_in_reference_data() {
        // Arrange: Construct payload matching claim_initiation___routing_decision_validation model
        Map<String, Object> payload = Map.of(
            "id", CLAIM_ID,
            "payload", Map.of(
                "addressId", ADDRESS_ID,
                "policyNumber", "POL-987654",
                "claimType", "AUTO"
            )
        );

        // Mock Redis/DynamoDB reference data lookup to simulate "not found"
        when(referenceDataLookup.resolveAddress(anyString())).thenReturn(Optional.empty());

        // Act: Invoke decision orchestration with structured input validation
        DecisionResult result = decisionService.evaluateClaimRouting(payload);

        // Assert: Verify routing decision reflects missing reference data
        assertNotNull(result, "Decision result must not be null");
        assertEquals("REJECTED", result.status(), "Should reject routing when address is missing");
        assertEquals("ADDRESS_NOT_FOUND_IN_REFERENCE", result.errorCode(), "Should return specific validation error code");
        assertEquals("reference_data_lookup", result.metadata().get("validationStep"), "Should log validation step for observability");

        // Verify external I/O contract: Redis/DynamoDB called exactly once with safe input
        verify(referenceDataLookup, times(1)).resolveAddress(ADDRESS_ID);
        verifyNoInteractions(routingOrchestrator, "Routing should not proceed without valid reference data");
    }

    // Minimal domain record for test isolation
    record DecisionResult(String status, String errorCode, Map<String, Object> metadata) {
        DecisionResult {
            if (metadata == null) metadata = Map.of();
        }
    }

    // Minimal interface simulating Cache & Reference Data (Redis/DynamoDB)
    interface ReferenceDataLookup {
        Optional<Map<String, Object>> resolveAddress(String addressId);
    }

    // Minimal interface simulating downstream routing service
    interface ClaimRoutingOrchestrator {
        void routeClaim(Map<String, Object> payload);
    }

    // Service under test (stateless, thread-safe, validates infra I/O contracts)
    static class ClaimInitiationRoutingDecisionService {
        private final ReferenceDataLookup referenceDataLookup;

        ClaimInitiationRoutingDecisionService(ReferenceDataLookup referenceDataLookup) {
            this.referenceDataLookup = referenceDataLookup;
        }

        DecisionResult evaluateClaimRouting(Map<String, Object> payload) {
            // Input validation & contract enforcement
            if (payload == null || !payload.containsKey("payload")) {
                throw new IllegalArgumentException("Invalid claim initiation payload structure");
            }

            Map<String, Object> innerPayload = (Map<String, Object>) payload.get("payload");
            String addressId = (String) innerPayload.get("addressId");

            // Mocked reference data lookup (simulates Redis/DynamoDB cache miss)
            Optional<Map<String, Object>> addressDetails = referenceDataLookup.resolveAddress(addressId);

            if (addressDetails.isEmpty()) {
                // Structured logging simulation for observability NFR
                // In production: logger.warn("Address not found in reference data", addressId);
                return new DecisionResult(
                    "REJECTED",
                    "ADDRESS_NOT_FOUND_IN_REFERENCE",
                    Map.of("validationStep", "reference_data_lookup", "timestamp", System.currentTimeMillis())
                );
            }

            // Fallback: Proceed to routing orchestration
            return new DecisionResult("ROUTED", null, Map.of());
        }
    }
}
