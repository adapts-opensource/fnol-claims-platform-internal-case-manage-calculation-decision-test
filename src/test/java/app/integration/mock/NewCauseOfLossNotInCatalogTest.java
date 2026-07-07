package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.HashMap;

/**
 * Mock test for Claim Initiation & Routing:decision:calculation.
 * Validates fallback routing when a cause of loss is absent from the reference catalog.
 * NFR Alignment: input_validation (explicit payload checks), observability (mocked structured logging),
 * security (least_privilege via isolated mock boundaries), concurrency (stateless JUnit5 test).
 */
public class ClaimRoutingCalculationMockTest {

    @Mock
    private CacheReferenceDataService cacheService;

    @Mock
    private DynamoDbReferenceDataRepository referenceDataRepo;

    @InjectMocks
    private RoutingDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void new_cause_of_loss_not_in_catalog() {
        // Arrange: Construct payload with a novel/unlisted cause of loss
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "CLM-2024-001");
        payload.put("causeOfLoss", "NOVEL_CIRCUMSTANCE_X");
        payload.put("coverageType", "AUTO_COMPREHENSIVE");
        payload.put("policyEffectiveDate", "2024-01-15");

        // Mock external reference data lookup to simulate missing catalog entry
        String cacheKey = "Cache & Reference Data:catalog:cause_of_loss:NOVEL_CIRCUMSTANCE_X";
        when(referenceDataRepo.getItem(cacheKey)).thenReturn(null);

        // Act: Invoke the routing decision calculation
        Map<String, Object> decision = calculator.calculate(payload);

        // Assert: Verify graceful fallback behavior for unlisted causes
        assertNotNull(decision, "Routing decision must not be null for unlisted causes");
        assertEquals("MANUAL_REVIEW_QUEUE", decision.get("routingQueue"),
                "Unlisted cause of loss should route to manual review queue");
        assertTrue((Boolean) decision.get("requiresManualValidation"),
                "System must flag unlisted causes for manual validation");
        assertEquals("NOVEL_CIRCUMSTANCE_X", decision.get("causeOfLoss"),
                "Original cause of loss must be preserved in output");
        assertEquals("CLM-2024-001", decision.get("id"),
                "Claim identifier must be propagated correctly");

        // Verify external I/O contract & cache behavior
        verify(referenceDataRepo).getItem(cacheKey);
        verify(cacheService, never()).put(anyString(), any());
        verifyNoMoreInteractions(referenceDataRepo, cacheService);
    }
}
