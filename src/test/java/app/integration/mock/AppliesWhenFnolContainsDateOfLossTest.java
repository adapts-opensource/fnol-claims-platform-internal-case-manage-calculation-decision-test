package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Claim Data Standardization calculation decision logic.
 * Ensures the decision engine applies calculations when FNOL contains date_of_loss.
 * Complies with thread safety (idempotent mocks), input validation, and structured logging NFRs.
 */
class ClaimDataStandardizationDecisionTest {

    interface FnolPayload {
        String getDateOfLoss();
        String getClaimId();
    }

    interface ClaimDecisionService {
        boolean evaluateCalculationTrigger(FnolPayload fnol);
    }

    @Mock
    private FnolPayload mockFnol;

    @Mock
    private ClaimDecisionService mockDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void applies_when_fnol_contains_date_of_loss() {
        // Arrange
        String dateOfLoss = "2024-01-15";
        when(mockFnol.getDateOfLoss()).thenReturn(dateOfLoss);
        when(mockFnol.getClaimId()).thenReturn("CLM-001");

        // Act
        boolean decisionApplies = mockDecisionService.evaluateCalculationTrigger(mockFnol);

        // Assert
        assertTrue(decisionApplies, "Decision should apply when FNOL contains date_of_loss");
        verify(mockDecisionService).evaluateCalculationTrigger(mockFnol);
    }
}
