package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SupplementalClaimForSameLossTest {

    @Mock
    private StateTransitionRepository stateRepository;

    @Mock
    private NotificationService notificationService;

    private SupplementalClaimProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new SupplementalClaimProcessor(stateRepository, notificationService);
    }

    @Test
    void supplemental_claim_for_same_loss() {
        // Arrange
        String claimId = "CLM-1001";
        String lossId = "LOSS-2002";
        String insuredEmail = "insured@example.com";

        when(stateRepository.getClaimState(claimId)).thenReturn(ClaimState.OPEN);
        when(stateRepository.isSameLossExisting(claimId, lossId)).thenReturn(true);
        when(stateRepository.transitionTo(claimId, ClaimState.SUPPLEMENTAL_RECEIVED)).thenReturn(ClaimState.SUPPLEMENTAL_RECEIVED);
        when(notificationService.sendSupplementalAcknowledgment(insuredEmail, claimId)).thenReturn(true);

        // Act
        ClaimState result = processor.processSupplementalSubmission(claimId, lossId, insuredEmail);

        // Assert
        assertEquals(ClaimState.SUPPLEMENTAL_RECEIVED, result);
        verify(stateRepository, times(1)).getClaimState(claimId);
        verify(stateRepository, times(1)).isSameLossExisting(claimId, lossId);
        verify(stateRepository, times(1)).transitionTo(claimId, ClaimState.SUPPLEMENTAL_RECEIVED);
        verify(notificationService, times(1)).sendSupplementalAcknowledgment(insuredEmail, claimId);
        verifyNoMoreInteractions(stateRepository, notificationService);
    }
}
