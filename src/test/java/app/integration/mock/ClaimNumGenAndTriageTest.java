package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Initiation & Routing:calculation:transformation.
 * Verifies claim number generation and standard routing logic.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimNumGenAndTriageTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @InjectMocks
    private ClaimCalculationTransformationService claimService;

    @Test
    void generate_claim_number_and_route_standard() {
        // Arrange
        String tenantCode = "FL01";
        int year = 2026;
        String product = "HO3";
        String causeOfLoss = "wind";
        String policyStatus = "active";
        String dateOfLoss = "2026-05-15";
        String severity = "low";
        boolean attorneyInvolved = false;

        // Mock policy validation to return active status
        when(policyValidationService.validatePolicy(anyString(), eq(tenantCode), eq(product), eq(policyStatus)))
            .thenReturn(new PolicyData("POL-987654321", policyStatus));

        // Mock rules engine to return standard routing for low severity, no attorney
        RoutingContext routingContext = new RoutingContext(product, causeOfLoss, severity, attorneyInvolved);
        when(rulesEngineService.determineRouting(any(RoutingContext.class)))
            .thenReturn(new RoutingResult("Standard property claim", "Investigation Pending"));

        // Mock document store to return a mock URI
        when(documentStoreService.storeClaimDocument(anyString(), anyString()))
            .thenReturn("s3://fnol-docs-bucket/claims/CLM-FL01-2026-00001234.json");

        // Act
        ClaimInitiationResult result = claimService.initiateClaim(
            tenantCode, year, product, causeOfLoss, policyStatus, dateOfLoss, severity, attorneyInvolved
        );

        // Assert
        assertNotNull(result, "Claim initiation result should not be null");
        
        // Verify claim number format: CLM-{TenantCode}-{YYYY}-{SequentialNumber}
        String claimNumber = result.getClaimNumber();
        assertTrue(claimNumber.matches("CLM-FL01-2026-\\d{4}"), 
            "Claim number should match CLM-TenantCode-Year-Seq format, got: " + claimNumber);
        
        // Verify claim type and routing state
        assertEquals("Standard property claim", result.getClaimType(), 
            "Claim type should be Standard property claim for HO3 wind/low severity");
        assertEquals("Investigation Pending", result.getRoutingState(), 
            "Claim should route to Investigation Pending state");

        // Verify external interactions
        verify(policyValidationService, times(1))
            .validatePolicy(anyString(), eq(tenantCode), eq(product), eq(policyStatus));
        
        verify(rulesEngineService, times(1))
            .determineRouting(any(RoutingContext.class));
        
        verify(documentStoreService, times(1))
            .storeClaimDocument(anyString(), anyString());
    }
}
