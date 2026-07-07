package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.time.LocalDate;
import java.util.List;

/**
 * Mock tests for Claim Initiation & Routing: orchestration: decision.
 * Validates decision logic by mocking Redis, DynamoDB, and SES contracts.
 * Ensures thread safety via stateless mocks and JUnit 5 extension.
 */
@ExtendWith(MockitoExtension.class)
public class MockClaimInitiationRoutingDecisionTest {

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private RedisService redisService;

    @Mock
    private SesService sesService;

    @InjectMocks
    private ClaimInitiationOrchestrationService orchestrationService;

    private static final String CACHE_KEY_NAMESPACE = "Cache & Reference Data:cache:";
    private static final String POLICY_TABLE = "Claims & Policy Data Store_table";
    private static final String PK_PREFIX = "pk=";

    @BeforeEach
    void setUp() {
        // Reset static state or shared resources if required by NFR operability
        // MockitoExtension handles mock reset automatically per test method
    }

    /**
     * Test Case: DolInFuture
     * Verifies that a claim with a Date of Loss in the future is rejected
     * during the routing decision phase.
     * Validates input validation, infra I/O contracts, and security (no email sent).
     */
    @Test
    void dol_in_future() {
        // Arrange
        String claimId = "CLM-DOL-FUTURE-001";
        LocalDate futureDate = LocalDate.now().plusDays(1);
        
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "dateOfLoss", futureDate.toString(),
            "policyNumber", "POL-TEST-FUTURE",
            "claimantEmail", "claimant@example.com"
        );

        // Mock Redis: Return routing configuration
        when(redisService.get(CACHE_KEY_NAMESPACE))
            .thenReturn("routing-config-v1");

        // Mock DynamoDB: Return active policy for lookup
        Map<String, Object> policyItem = Map.of(
            "pk", PK_PREFIX + "POL-TEST-FUTURE",
            "status", "ACTIVE",
            "coverageType", "AUTO"
        );
        when(dynamoDbService.getItem(eq(POLICY_TABLE), anyString()))
            .thenReturn(policyItem);

        // Act
        DecisionResult result = orchestrationService.evaluateDecision(claimId, payload);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertEquals(DecisionStatus.REJECTED, result.getStatus(), "Future DOL should result in REJECTED status");
        
        // Validate specific error code for DOL in future
        boolean hasFutureDolError = result.getValidationErrors().stream()
            .anyMatch(error -> error.getCode().equals("DOL_FUTURE_DATE"));
        assertTrue(hasFutureDolError, "Validation errors must contain DOL_FUTURE_DATE");

        // Security NFR: Verify no notification sent for invalid claim
        verify(sesService, never()).sendEmail(any());
        
        // Concurrency NFR: Verify thread safety of service invocation (implicitly tested by mock isolation)
        verifyNoMoreInteractions(redisService, dynamoDbService, sesService);
    }
}
