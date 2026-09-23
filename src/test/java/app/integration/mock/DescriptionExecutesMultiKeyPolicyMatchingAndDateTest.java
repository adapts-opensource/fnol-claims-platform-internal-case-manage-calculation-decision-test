package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

// Supporting interfaces and SUT for test isolation
interface PolicyMatchingService {
    List<String> matchMultiKey(Map<String, Object> payload);
}

interface DateOfLossValidator {
    boolean validatePolicyPeriod(Map<String, Object> payload);
}

interface CatRestrictionService {
    boolean checkCatRestrictions(Map<String, Object> payload);
}

class RoutingDecisionResult {
    private final String claimId;
    private final boolean policyMatched;
    private final boolean dateOfLossValid;
    private final boolean catRestricted;

    RoutingDecisionResult(String claimId, boolean policyMatched, boolean dateOfLossValid, boolean catRestricted) {
        this.claimId = claimId;
        this.policyMatched = policyMatched;
        this.dateOfLossValid = dateOfLossValid;
        this.catRestricted = catRestricted;
    }

    public String getClaimId() { return claimId; }
    public boolean isPolicyMatched() { return policyMatched; }
    public boolean isDateOfLossValid() { return dateOfLossValid; }
    public boolean isCatRestricted() { return catRestricted; }
}

class ClaimDecisionCalculationService {
    private final PolicyMatchingService policyMatchingService;
    private final DateOfLossValidator dateOfLossValidator;
    private final CatRestrictionService catRestrictionService;

    ClaimDecisionCalculationService(PolicyMatchingService policyMatchingService, 
                                    DateOfLossValidator dateOfLossValidator, 
                                    CatRestrictionService catRestrictionService) {
        this.policyMatchingService = policyMatchingService;
        this.dateOfLossValidator = dateOfLossValidator;
        this.catRestrictionService = catRestrictionService;
    }

    RoutingDecisionResult calculateDecision(Map<String, Object> payload) {
        List<String> matchedKeys = policyMatchingService.matchMultiKey(payload);
        boolean dateValid = dateOfLossValidator.validatePolicyPeriod(payload);
        boolean catRestricted = catRestrictionService.checkCatRestrictions(payload);
        
        return new RoutingDecisionResult(
            String.valueOf(payload.get("claimId")),
            !matchedKeys.isEmpty(),
            dateValid,
            catRestricted
        );
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private PolicyMatchingService policyMatchingService;
    
    @Mock
    private DateOfLossValidator dateOfLossValidator;
    
    @Mock
    private CatRestrictionService catRestrictionService;

    private ClaimDecisionCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new ClaimDecisionCalculationService(policyMatchingService, dateOfLossValidator, catRestrictionService);
    }

    @Test
    void description_executes_multi_key_policy_matching_and_date_of_loss_validation_against_policy_period_and_cat_restrictions() {
        // Arrange
        String claimId = "claim-789";
        Map<String, Object> payload = Map.of(
            "claimId", claimId,
            "dateOfLoss", "2024-05-20",
            "policyNumber", "POL-XYZ-456",
            "coverageType", "AUTO",
            "zone", "ZONE-A"
        );

        // Mock multi-key policy matching
        List<String> expectedMatches = List.of("POL-XYZ-456", "AUTO", "ZONE-A");
        when(policyMatchingService.matchMultiKey(payload)).thenReturn(expectedMatches);

        // Mock date-of-loss validation against policy period
        when(dateOfLossValidator.validatePolicyPeriod(payload)).thenReturn(true);

        // Mock CAT restriction check
        when(catRestrictionService.checkCatRestrictions(payload)).thenReturn(false);

        // Act
        RoutingDecisionResult result = calculationService.calculateDecision(payload);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(claimId, result.getClaimId());
        assertTrue(result.isPolicyMatched(), "Should match policies via multiple keys");
        assertTrue(result.isDateOfLossValid(), "Date of loss should be within policy period");
        assertFalse(result.isCatRestricted(), "Should not be restricted by CAT rules");

        // Verify interactions
        verify(policyMatchingService, times(1)).matchMultiKey(payload);
        verify(dateOfLossValidator, times(1)).validatePolicyPeriod(payload);
        verify(catRestrictionService, times(1)).checkCatRestrictions(payload);
        verifyNoMoreInteractions(policyMatchingService, dateOfLossValidator, catRestrictionService);
    }
}
