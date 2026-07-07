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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private ClaimRoutingOrchestrationService orchestrationService;

    @InjectMocks
    private ClaimInitiationRoutingDecisionValidator validator;

    @BeforeEach
    void setUp() {
        reset(orchestrationService);
    }

    @Test
    void dol_equals_expiration_date() {
        // Arrange: DOL equals expiration date
        LocalDate current = LocalDate.now();
        Map<String, Object> payload = Map.of(
                "id", "claim-init-123",
                "dol", current.toString(),
                "expirationDate", current.toString()
        );

        DecisionResult expected = new DecisionResult("VALID", "ROUTING_APPROVED", Map.of("rule", "DOL_EQ_EXPIRATION"));
        when(orchestrationService.evaluate(any(Map.class))).thenReturn(expected);

        // Act: Trigger validation and routing decision
        DecisionResult actual = validator.process(payload);

        // Assert: Verify outcome matches expected behavior when DOL == expiration date
        assertNotNull(actual);
        assertEquals("VALID", actual.status());
        assertEquals("ROUTING_APPROVED", actual.outcome());
        assertEquals("DOL_EQ_EXPIRATION", actual.metadata().get("rule"));

        verify(orchestrationService, times(1)).evaluate(payload);
    }

    private static class DecisionResult {
        private final String status;
        private final String outcome;
        private final Map<String, String> metadata;

        DecisionResult(String status, String outcome, Map<String, String> metadata) {
            this.status = status;
            this.outcome = outcome;
            this.metadata = metadata;
        }

        String status() { return status; }
        String outcome() { return outcome; }
        Map<String, String> metadata() { return metadata; }
    }
}
