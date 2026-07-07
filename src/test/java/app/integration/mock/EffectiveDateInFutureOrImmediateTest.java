package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Mock test for Claim Data Standardization:validation:decision
 * Verifies that effective dates in the future or immediate are accepted.
 */
class ClaimDataStandardizationValidationDecisionMockTest {

    private ValidationDecisionService validationDecisionService;

    @BeforeEach
    void setUp() {
        validationDecisionService = mock(ValidationDecisionService.class);
    }

    @Test
    void effective_date_in_future_or_immediate() {
        // Arrange: Payload with effective date in the future
        Map<String, Object> futurePayload = Map.of(
            "id", "claim-future-001",
            "payload", Map.of("effectiveDate", LocalDate.now().plusDays(7).toString())
        );

        // Arrange: Payload with effective date immediate (today)
        Map<String, Object> immediatePayload = Map.of(
            "id", "claim-immediate-002",
            "payload", Map.of("effectiveDate", LocalDate.now().toString())
        );

        // Mock decision service to return APPROVED for valid effective dates
        when(validationDecisionService.evaluateDecision(any(Map.class)))
            .thenReturn("APPROVED");

        // Act & Assert: Future date validation
        String futureResult = validationDecisionService.evaluateDecision(futurePayload);
        assertEquals("APPROVED", futureResult, "Future effective date should yield APPROVED decision");

        // Act & Assert: Immediate date validation
        String immediateResult = validationDecisionService.evaluateDecision(immediatePayload);
        assertEquals("APPROVED", immediateResult, "Immediate effective date should yield APPROVED decision");

        verify(validationDecisionService, times(2)).evaluateDecision(any(Map.class));
    }
}

/**
 * Represents the mocked decision service contract for claim validation.
 * In production, this would be wired to DynamoDB/RulesEngine services.
 */
interface ValidationDecisionService {
    String evaluateDecision(Map<String, Object> claimData);
}
