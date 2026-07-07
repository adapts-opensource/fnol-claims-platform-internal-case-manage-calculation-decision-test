package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private CacheClient cacheClient;

    private ClaimRoutingDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ClaimRoutingDecisionCalculator(cacheClient);
    }

    @Test
    void cause_of_loss_must_match_catalog() {
        // Arrange
        String validCauseCode = "VEHICLE_COLLISION";
        Map<String, Object> claimPayload = Map.of(
            "id", "claim-123",
            "causeOfLoss", validCauseCode
        );
        String catalogCacheKey = "routing:catalog:cause_of_loss";
        Set<String> validCatalogCodes = Set.of("VEHICLE_COLLISION", "THEFT", "FIRE");
        
        when(cacheClient.get(catalogCacheKey)).thenReturn(validCatalogCodes);

        // Act
        RoutingDecision decision = calculator.calculate(claimPayload);

        // Assert
        assertNotNull(decision, "Decision should not be null");
        assertEquals(RoutingDecision.Status.VALID, decision.status());
        assertTrue(decision.isValid(), "Decision should be valid when cause matches catalog");
        verify(cacheClient).get(catalogCacheKey);
    }

    // Minimal domain interfaces/classes to support compilation and isolation
    interface CacheClient {
        Set<String> get(String key);
    }

    static class ClaimRoutingDecisionCalculator {
        private final CacheClient cacheClient;
        
        ClaimRoutingDecisionCalculator(CacheClient cacheClient) {
            this.cacheClient = cacheClient;
        }

        RoutingDecision calculate(Map<String, Object> payload) {
            String causeOfLoss = (String) payload.get("causeOfLoss");
            Set<String> catalog = cacheClient.get("routing:catalog:cause_of_loss");
            
            if (catalog == null || !catalog.contains(causeOfLoss)) {
                throw new IllegalArgumentException("Cause of loss must match catalog");
            }
            
            return new RoutingDecision(RoutingDecision.Status.VALID, true);
        }
    }

    record RoutingDecision(Status status, boolean isValid) {
        enum Status { VALID, INVALID }
    }
}
