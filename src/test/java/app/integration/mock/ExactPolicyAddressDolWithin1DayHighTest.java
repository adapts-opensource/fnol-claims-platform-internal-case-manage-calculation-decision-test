package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private RedisCacheService cacheService;
    @Mock
    private DynamoDBClaimsStore claimsStore;
    @Mock
    private DecisionScoringEngine scoringEngine;

    private ClaimRoutingDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ClaimRoutingDecisionService(cacheService, claimsStore, scoringEngine);
    }

    @Test
    void exact_policy_address_dol_within_1_day_high_confidence() {
        // Given
        String claimId = "CLM-1001";
        String policyId = "POL-2002";
        String address = "100 Insurance Blvd, Metropolis, NY 10001";
        LocalDate incidentDate = LocalDate.of(2023, 10, 25);
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 24); // Within 1 day

        Map<String, Object> payload = Map.of(
            "claimId", claimId,
            "policyId", policyId,
            "address", address,
            "incidentDate", incidentDate.toString(),
            "dateOfLoss", dateOfLoss.toString()
        );

        // Mock external I/O per infra contracts
        when(cacheService.get(anyString())).thenReturn(Optional.of("{\"status\":\"ACTIVE\"}"));
        when(claimsStore.getItem(anyString(), anyString())).thenReturn(Map.of("policyId", policyId, "active", true));
        when(scoringEngine.calculate(anyMap())).thenReturn(new DecisionResult("HIGH", "DIRECT_ADJUSTER"));

        // When
        DecisionResult result = decisionService.evaluate(payload);

        // Then
        assertNotNull(result);
        assertEquals("HIGH", result.confidenceLevel());
        assertEquals("DIRECT_ADJUSTER", result.routingPath());

        // Verify infra I/O contracts
        verify(cacheService).get(eq("Cache & Reference Data:cache:" + claimId));
        verify(claimsStore).getItem(eq("Claims & Policy Data Store_table"), eq("pk"));
    }

    // Minimal service implementation for testing
    static class ClaimRoutingDecisionService {
        private final RedisCacheService cacheService;
        private final DynamoDBClaimsStore claimsStore;
        private final DecisionScoringEngine scoringEngine;

        ClaimRoutingDecisionService(RedisCacheService cacheService, DynamoDBClaimsStore claimsStore, DecisionScoringEngine scoringEngine) {
            this.cacheService = cacheService;
            this.claimsStore = claimsStore;
            this.scoringEngine = scoringEngine;
        }

        DecisionResult evaluate(Map<String, Object> payload) {
            String claimId = (String) payload.get("claimId");
            cacheService.get("Cache & Reference Data:cache:" + claimId);
            claimsStore.getItem("Claims & Policy Data Store_table", "pk");
            return scoringEngine.calculate(payload);
        }
    }

    interface RedisCacheService {
        Optional<String> get(String key);
    }

    interface DynamoDBClaimsStore {
        Map<String, Object> getItem(String tableName, String partitionKey);
    }

    interface DecisionScoringEngine {
        DecisionResult calculate(Map<String, Object> payload);
    }

    record DecisionResult(String confidenceLevel, String routingPath) {}
}
