package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class StateTransitionRollbackIntegrationMockTest {

    @Mock
    private ReserveLineRepository reserveLineRepository;

    @Mock
    private SesCommunicationService sesCommunicationService;

    @Mock
    private S3DocumentStoreService s3DocumentStoreService;

    @InjectMocks
    private InsuredEngagementStateTransitionService stateTransitionService;

    private static final String CLAIM_ID = "claim-123";
    private static final String RESERVE_ID = "reserve-456";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void rollback_required() {
        // Arrange: Simulate a state transition that fails during document persistence, triggering rollback
        String transitionId = UUID.randomUUID().toString();
        ReserveLine mockReserve = mock(ReserveLine.class);
        when(mockReserve.getReserveId()).thenReturn(RESERVE_ID);
        when(reserveLineRepository.findById(RESERVE_ID)).thenReturn(Optional.of(mockReserve));
        when(s3DocumentStoreService.storeDocument(anyString(), anyString())).thenThrow(new RuntimeException("S3 transient failure"));

        // Act & Assert: Verify rollback is triggered on failure
        assertThrows(RuntimeException.class, () ->
            stateTransitionService.executeTransition(CLAIM_ID, transitionId, "APPROVED")
        );

        // Verify rollback behavior: state reverted, no downstream side effects
        verify(reserveLineRepository).save(argThat(reserve ->
            reserve.getReserveId().equals(RESERVE_ID) &&
            reserve.getApprovalStatus().equals("PENDING")
        ));
        verify(sesCommunicationService, never()).sendNotification(anyString(), anyList(), anyString());
        verify(s3DocumentStoreService).storeDocument(anyString(), anyString());
    }
}
