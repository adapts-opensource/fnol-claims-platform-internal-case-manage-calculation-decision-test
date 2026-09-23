package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies status transition validation for Claim Initiation & Routing decision calculation.
 * NFR Compliance: Input validation enforced via assertions; thread-safe via JUnit 5 isolation.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationStatusTransitionTest {

    @Mock
    private ClaimRoutingDecisionRepository claimRepository;

    @Mock
    private StatusTransitionValidator statusValidator;

    @Mock
    private CommunicationService communicationService;

    private ClaimRoutingDecisionCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new ClaimRoutingDecisionCalculationService(
                claimRepository, statusValidator, communicationService
        );
    }

    @Test
    void status_must_be_valid_transition_target() {
        String claimId = "claim-uuid-12345";
        String currentStatus = "INITIATED";
        String invalidTargetStatus = "PAID";
        String validTargetStatus = "ROUTING_ASSIGNED";

        // Arrange: Mock repository to return existing claim payload
        Map<String, Object> mockPayload = Map.of("status", currentStatus, "calculation", "PENDING");
        when(claimRepository.findByClaimId(claimId)).thenReturn(Optional.of(mockPayload));

        // Act & Assert: Invalid transition must be rejected with clear input validation error
        when(statusValidator.isValidTransition(currentStatus, invalidTargetStatus)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () ->
                calculationService.processDecisionCalculation(claimId, invalidTargetStatus)
        );
        verify(communicationService, never()).sendAcknowledgment(anyString(), anyList());

        // Act & Assert: Valid transition must proceed and persist state
        when(statusValidator.isValidTransition(currentStatus, validTargetStatus)).thenReturn(true);
        doNothing().when(claimRepository).updateStatus(eq(claimId), eq(validTargetStatus));

        assertDoesNotThrow(() ->
                calculationService.processDecisionCalculation(claimId, validTargetStatus)
        );
        verify(claimRepository).updateStatus(claimId, validTargetStatus);
        verify(communicationService).sendAcknowledgment(eq(claimId), anyList());
    }
}
