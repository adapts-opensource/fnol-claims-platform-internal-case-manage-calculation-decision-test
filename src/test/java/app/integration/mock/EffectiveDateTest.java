package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StateTransitionTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private DateValidator dateValidator;

    @InjectMocks
    private InsuredEngagementStateTransitionService stateTransitionService;

    @Test
    void effective_date() {
        // Arrange
        String claimId = "CLM-998877";
        LocalDate expectedEffectiveDate = LocalDate.now();
        String targetState = "APPROVED";

        Claim mockClaim = mock(Claim.class);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(mockClaim));
        when(dateValidator.isValidForTransition(expectedEffectiveDate)).thenReturn(true);

        // Act
        StateTransitionResult result = stateTransitionService.transitionState(claimId, targetState, expectedEffectiveDate);

        // Assert
        assertNotNull(result, "State transition result should not be null");
        assertEquals(expectedEffectiveDate, result.getEffectiveDate(), "Effective date should match the validated input");
        assertEquals(targetState, result.getTargetState(), "Target state should be correctly assigned");
        verify(dateValidator).isValidForTransition(expectedEffectiveDate);
        verify(claimRepository).save(any());
    }
}
