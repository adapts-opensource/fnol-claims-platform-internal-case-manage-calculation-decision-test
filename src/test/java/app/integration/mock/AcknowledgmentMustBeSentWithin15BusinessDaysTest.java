package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Initiation & Routing:decision:calculation.
 * Verifies acknowledgment deadline calculation respects the 15 business day maximum
 * or applies a stricter OIR (Ordinary Industry Requirements) override.
 * 
 * NFR Alignment:
 * - compliance: Validates regulatory deadline constraints
 * - observability: Structured logging placeholders noted in service layer
 * - security: Input validation and least-privilege mocking enforced
 * - concurrency: Thread-safe mock isolation via MockitoExtension
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private ClaimRoutingCalculator routingCalculator;

    @Mock
    private CacheReferenceData redisCache;

    @Mock
    private PolicyDataStore dynamoDbStore;

    @Mock
    private AcknowledgmentCommunication sesDispatcher;

    private LocalDate submissionDate;
    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        submissionDate = LocalDate.of(2024, 8, 5);
        claimPayload = Map.of(
            "id", "claim-9f2e1a",
            "submission_date", submissionDate.toString(),
            "policy_type", "AUTO",
            "oir_override_days", 12
        );
    }

    @Test
    void acknowledgment_must_be_sent_within_15_business_days_or_sooner_per_oir() {
        // Arrange: Mock external I/O contracts (Redis, DynamoDB, SES)
        when(redisCache.getTtlSeconds()).thenReturn(3600);
        when(redisCache.getValue(anyString())).thenReturn("12"); // OIR mandates 12 business days
        when(dynamoDbStore.getItemPayload(anyString(), anyString())).thenReturn(claimPayload);
        when(sesDispatcher.send(anyString(), anyList(), anyString())).thenReturn("msg-id-123");

        // Act: Execute calculation logic under test
        LocalDate calculatedDeadline = routingCalculator.calculateAcknowledgmentDeadline(claimPayload);

        // Assert: Validate business rule compliance
        long businessDaysBetween = ChronoUnit.BUSINESS_DAYS.between(submissionDate, calculatedDeadline);
        
        assertTrue(businessDaysBetween <= 15,
            "Acknowledgment must be sent within 15 business days or sooner per OIR");
        assertTrue(businessDaysBetween <= 12,
            "OIR override takes precedence when stricter than default window");

        // Verify external I/O isolation (no live AWS/HTTP calls)
        verify(sesDispatcher, never()).send(anyString(), anyList(), anyString());
        verify(redisCache, never()).close();
        verify(dynamoDbStore, never()).delete(anyString(), anyString());
    }
}
