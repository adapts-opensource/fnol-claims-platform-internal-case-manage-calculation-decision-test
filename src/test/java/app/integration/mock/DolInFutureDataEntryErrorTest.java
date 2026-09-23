package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementStateTransitionServiceTest {

    @Mock
    private EngagementStateRepository stateRepository;

    @Mock
    private ClaimValidationService validationService;

    @Mock
    private CommunicationService communicationService;

    @InjectMocks
    private InsuredEngagementStateTransitionService stateTransitionService;

    @Test
    void dolInFutureDataEntryError() {
        // Given
        String claimId = "CLM-1001";
        LocalDate futureDateOfLoss = LocalDate.now().plusDays(10);
        when(validationService.isDateOfLossValid(futureDateOfLoss)).thenReturn(false);
        when(validationService.getErrorMessage()).thenReturn("DOL in future (data entry error)");

        // When
        EngagementState result = stateTransitionService.processStateTransition(claimId, futureDateOfLoss);

        // Then
        assertEquals(EngagementState.DATA_ENTRY_ERROR, result);
        verify(stateRepository).saveState(eq(claimId), eq(EngagementState.DATA_ENTRY_ERROR));
        verify(communicationService).sendAlert(eq(claimId), anyString());
        verifyNoMoreInteractions(stateRepository, validationService, communicationService);
    }
}
