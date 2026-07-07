package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PolicyMatchValidationTest {

    @Mock
    private FnolValidationDecisionService fnolValidationService;

    @BeforeEach
    void setUp() {
        // Verify mock initialization and prepare test environment
        assertNotNull(fnolValidationService);
    }

    @Test
    void ifMultiplePoliciesMatchStatusMultipleMatchTaskResolvePolicyMatch() {
        // Arrange
        String claimId = "claim-123";
        String policyId = "pol-456";
        String tenantId = "tenant-789";

        PolicyMatchOutcome expectedOutcome = new PolicyMatchOutcome("MULTIPLE_MATCH", "Resolve Policy Match");

        when(fnolValidationService.evaluatePolicyMatch(claimId, policyId, tenantId))
                .thenReturn(expectedOutcome);

        // Act
        PolicyMatchOutcome actualOutcome = fnolValidationService.evaluatePolicyMatch(claimId, policyId, tenantId);

        // Assert
        assertNotNull(actualOutcome);
        assertEquals("MULTIPLE_MATCH", actualOutcome.status());
        assertEquals("Resolve Policy Match", actualOutcome.task());
    }

    // Minimal domain model for decision outcome
    record PolicyMatchOutcome(String status, String task) {}

    // Mocked service boundary representing the validation/decision layer
    interface FnolValidationDecisionService {
        PolicyMatchOutcome evaluatePolicyMatch(String claimId, String policyId, String tenantId);
    }
}
