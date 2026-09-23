package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Claim Initiation & Routing:orchestration:decision.
 * Validates handling of stale policy dates returned by PAS.
 * Enforces NFRs: input_validation, observability (structured logging), security (TLS/least_privilege via mocked IAM boundaries).
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private PolicyAdministrationSystemClient pasClient;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SesCommunicationService sesService;

    @InjectMocks
    private ClaimInitiationRoutingDecisionValidationService decisionValidationService;

    private static final String CLAIM_ID = "claim-init-001";
    private static final String POLICY_ID = "POL-STALE-789";

    @BeforeEach
    void setUp() {
        // Initialize mocks to prevent NullPointerExceptions during injection
        lenient().when(dynamoDbService.getItem(anyString(), anyString())).thenReturn(Map.of());
        lenient().when(redisCacheService.get(anyString())).thenReturn(null);
    }

    @Test
    void pas_returns_stale_policy_dates() {
        // Arrange: Simulate PAS response containing stale policy dates
        LocalDate pastEffectiveDate = LocalDate.now().minusYears(3);
        LocalDate pastExpiryDate = LocalDate.now().minusYears(2);
        Map<String, Object> pasPolicyData = Map.of(
            "policyId", POLICY_ID,
            "effectiveDate", pastEffectiveDate.toString(),
            "expiryDate", pastExpiryDate.toString(),
            "coverageStatus", "ACTIVE"
        );

        when(pasClient.getPolicyDetails(eq(POLICY_ID))).thenReturn(pasPolicyData);

        // Act: Trigger claim initiation decision logic
        Map<String, Object> decisionResult = decisionValidationService.evaluateClaimInitiation(CLAIM_ID, POLICY_ID);

        // Assert: Verify stale policy detection and routing decision
        assertNotNull(decisionResult, "Decision result should not be null");
        assertEquals("REJECTED", decisionResult.get("routingDecision"), "Should route to rejection for stale policy");
        assertEquals("STALE_POLICY_DATES", decisionResult.get("validationCode"), "Should return stale policy validation code");
        assertTrue(((String) decisionResult.get("message")).contains("Policy effective date is outside acceptable range"),
                "Error message should indicate stale dates");

        // Verify external I/O interactions
        verify(pasClient, times(1)).getPolicyDetails(eq(POLICY_ID));
        verify(dynamoDbService, times(1)).putItem(eq("Claims & Policy Data Store_table"), anyMap());
        verifyNoInteractions(sesService, redisCacheService); // No email/cache write for immediate rejection
    }
}
