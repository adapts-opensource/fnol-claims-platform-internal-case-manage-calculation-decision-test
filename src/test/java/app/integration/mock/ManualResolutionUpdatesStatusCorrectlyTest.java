package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManualResolutionUpdatesStatusCorrectlyTest {

    @Mock
    private InsuredEngagementStateTransitionService stateTransitionService;

    @Mock
    private DataPersistenceRepository dataPersistenceRepository;

    private InsuredEngagementStateTransitionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InsuredEngagementStateTransitionHandler(stateTransitionService, dataPersistenceRepository);
    }

    @Test
    void manual_resolution_updates_status_correctly() {
        // Arrange
        String reserveId = "reserve-001";
        String initialStatus = "PENDING";
        String expectedStatus = "RESOLVED";

        when(stateTransitionService.fetchCurrentState(reserveId)).thenReturn(initialStatus);

        // Act
        handler.applyManualResolutionDecision(reserveId);

        // Assert
        verify(stateTransitionService, times(1)).updateState(reserveId, expectedStatus);
        verify(dataPersistenceRepository, times(1)).persistStateChange(eq(reserveId), eq(expectedStatus));
        assertEquals(expectedStatus, handler.getReserveState(reserveId));
    }
}
