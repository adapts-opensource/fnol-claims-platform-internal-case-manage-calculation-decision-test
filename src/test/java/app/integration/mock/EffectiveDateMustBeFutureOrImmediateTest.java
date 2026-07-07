package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
public class StateTransitionEffectiveDateTest {

    @Mock
    private DataPersistenceService dataPersistenceService;

    @Mock
    private CommunicationService communicationService;

    private StateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        stateTransitionService = new StateTransitionService(dataPersistenceService, communicationService);
    }

    @Test
    void effective_date_must_be_future_or_immediate() {
        LocalDate immediateDate = LocalDate.now();
        LocalDate futureDate = LocalDate.now().plusDays(1);
        LocalDate pastDate = LocalDate.now().minusDays(1);

        assertDoesNotThrow(() -> stateTransitionService.transition("INS-001", "ACTIVE", immediateDate));
        assertDoesNotThrow(() -> stateTransitionService.transition("INS-001", "ACTIVE", futureDate));
        assertThrows(IllegalArgumentException.class, () -> stateTransitionService.transition("INS-001", "ACTIVE", pastDate));

        verify(dataPersistenceService, never()).save(any());
        verify(communicationService, never()).sendNotification(anyString(), anyList());
    }
}
