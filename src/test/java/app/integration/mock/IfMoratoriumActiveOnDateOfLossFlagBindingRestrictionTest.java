package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Multi-Channel FNOL Submission:orchestration:validation.
 * Verifies that a binding restriction is correctly flagged when a moratorium
 * is active on the provided date_of_loss.
 */
class MultiChannelFnolSubmissionOrchestrationValidationMockTest {

    @Mock
    private MoratoriumOrchestrationService moratoriumService;

    private OrchestrationValidationEngine validationEngine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validationEngine = new OrchestrationValidationEngine(moratoriumService);
    }

    @Test
    void if_moratorium_active_on_date_of_loss_flag_binding_restriction() {
        // Arrange: Create context with a specific date_of_loss
        FnolSubmissionContext context = new FnolSubmissionContext();
        context.setDateOfLoss("2024-09-15");

        // Mock external moratorium check to return active status
        MoratoriumStatus moratoriumStatus = new MoratoriumStatus();
        moratoriumStatus.setActive(true);
        when(moratoriumService.isMoratoriumActiveOn(context.getDateOfLoss()))
                .thenReturn(moratoriumStatus);

        // Act: Run orchestration validation
        BindingRestrictionResult result = validationEngine.evaluateBindingRestrictions(context);

        // Assert: Verify binding restriction is flagged and reason is recorded
        assertTrue(result.isBindingRestricted(),
                "Binding restriction must be flagged when moratorium is active on date_of_loss");
        assertEquals("MORATORIUM_ACTIVE_ON_DATE_OF_LOSS", result.getRestrictionReason(),
                "Restriction reason must match expected enum/value");
        verify(moratoriumService).isMoratoriumActiveOn(context.getDateOfLoss());
    }

    // --- Domain Models & Interfaces (Mocked in Integration Layer) ---

    class FnolSubmissionContext {
        private String dateOfLoss;
        public String getDateOfLoss() { return dateOfLoss; }
        public void setDateOfLoss(String dateOfLoss) { this.dateOfLoss = dateOfLoss; }
    }

    class MoratoriumStatus {
        private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    class BindingRestrictionResult {
        private boolean bindingRestricted = false;
        private String restrictionReason;

        public boolean isBindingRestricted() { return bindingRestricted; }
        public void setBindingRestricted(boolean bindingRestricted) { this.bindingRestricted = bindingRestricted; }
        public String getRestrictionReason() { return restrictionReason; }
        public void setRestrictionReason(String restrictionReason) { this.restrictionReason = restrictionReason; }
    }

    interface MoratoriumOrchestrationService {
        MoratoriumStatus isMoratoriumActiveOn(String dateOfLoss);
    }

    class OrchestrationValidationEngine {
        private final MoratoriumOrchestrationService moratoriumService;

        OrchestrationValidationEngine(MoratoriumOrchestrationService moratoriumService) {
            this.moratoriumService = moratoriumService;
        }

        BindingRestrictionResult evaluateBindingRestrictions(FnlSubmissionContext context) {
            BindingRestrictionResult result = new BindingRestrictionResult();
            if (context == null || context.getDateOfLoss() == null || context.getDateOfLoss().isBlank()) {
                return result;
            }
            MoratoriumStatus status = moratoriumService.isMoratoriumActiveOn(context.getDateOfLoss());
            if (status != null && status.isActive()) {
                result.setBindingRestricted(true);
                result.setRestrictionReason("MORATORIUM_ACTIVE_ON_DATE_OF_LOSS");
            }
            return result;
        }
    }
}
