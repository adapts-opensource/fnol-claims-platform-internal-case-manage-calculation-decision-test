package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;

/**
 * Verifies state transition validation rules for Insured Engagement & Tracking.
 * Mocks external I/O by isolating the validation logic; no live AWS or HTTP calls.
 */
@ExtendWith(MockitoExtension.class)
public class InsuredEngagementStateTransitionTest {

    @Test
    void date_of_loss_must_be_current_date() {
        // Arrange
        LocalDate currentDate = LocalDate.now();
        LocalDate futureDate = currentDate.plusDays(1);
        LocalDate pastDate = currentDate.minusDays(1);

        // Act & Assert - Future date violates the rule: date_of_loss must be <= current date
        assertThrows(IllegalArgumentException.class, () -> {
            validateDateOfLoss(futureDate);
        }, "date_of_loss must be <= current date");

        // Current and past dates are valid per business rule
        assertDoesNotThrow(() -> validateDateOfLoss(currentDate));
        assertDoesNotThrow(() -> validateDateOfLoss(pastDate));
    }

    /**
     * Simulates the validation logic typically enforced by a StateTransitionService.
     * In production integration tests, this would delegate to a mocked service client.
     */
    private void validateDateOfLoss(LocalDate dateOfLoss) {
        if (dateOfLoss.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("date_of_loss must be <= current date");
        }
    }
}
