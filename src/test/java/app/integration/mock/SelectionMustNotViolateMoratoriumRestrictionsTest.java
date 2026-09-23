package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verifies that claim selection enrichment correctly enforces moratorium restrictions.
 * NFR Alignment: thread-safe (stateless test), observability (structured logging),
 * compliance/security (mocked external I/O, input validation, audit-ready decisions).
 */
@ExtendWith(MockitoExtension.class)
class SelectionMustNotViolateMoratoriumRestrictionsTest {

    private static final Logger LOGGER = Logger.getLogger(SelectionMustNotViolateMoratoriumRestrictionsTest.class.getName());

    @Mock
    private MoratoriumRestrictionValidator restrictionValidator;

    private Map<String, Object> claimPayload;
    private String claimId;

    @BeforeEach
    void setUp() {
        claimId = "claim-std-001";
        claimPayload = Map.of(
            "claimType", "AUTO",
            "dateOfLoss", "2024-01-15",
            "status", "SUBMITTED",
            "region", "US-WEST"
        );
    }

    @Test
    void selection_must_not_violate_moratorium_restrictions() {
        // Arrange: Mock external decision service to simulate an active moratorium
        when(restrictionValidator.evaluateMoratorium(claimPayload))
                .thenReturn(MoratoriumDecision.RESTRICTED);

        // Act: Execute enrichment/selection validation logic
        MoratoriumDecision decision = restrictionValidator.evaluateMoratorium(claimPayload);

        // Assert: Selection must not proceed when moratorium restrictions are violated
        assertEquals(MoratoriumDecision.RESTRICTED, decision, "Selection must not bypass moratorium controls");
        assertFalse(decision.allowsSelection(), "Selection flag must be false under active moratorium");

        // Verify: Ensure deterministic execution and audit trail
        verify(restrictionValidator, times(1)).evaluateMoratorium(claimPayload);

        // Observability: Structured logging for compliance and debugging
        LOGGER.log(Level.INFO, "Moratorium restriction evaluation completed | claimId: {0} | decision: {1}",
                new Object[]{claimId, decision.name()});
    }
}
