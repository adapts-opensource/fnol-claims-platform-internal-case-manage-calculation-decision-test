package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for feature: Insured Engagement & Tracking:decision:state_transition.
 * Verifies state transition logic when a Moratorium is declared on the same day as the Date of Loss (DOL).
 */
@ExtendWith(MockitoExtension.class)
public class MoratoriumDeclaredSameDayAsDolTest {

    @Mock
    private StateTransitionValidator stateTransitionValidator;

    @Mock
    private MoratoriumService moratoriumService;

    @Mock
    private ReserveLineRepository reserveLineRepository;

    @Mock
    private InsuredEngagementService insuredEngagementService;

    @InjectMocks
    private StateTransitionService stateTransitionService;

    private LocalDate currentDate;
    private String claimId;

    @BeforeEach
    void setUp() {
        currentDate = LocalDate.now();
        claimId = "CLM-MOR-SAME-DAY-001";
    }

    /**
     * Test Case: MoratoriumDeclaredSameDayAsDol
     * Description: Moratorium declared same day as DOL.
     * 
     * Verifies that the system correctly handles the state transition when a moratorium
     * is active on the exact same date as the Date of Loss, ensuring proper validation,
     * reserve line handling, and engagement tracking.
     */
    @Test
    void moratorium_declared_same_day_as_dol() {
        // Given: Moratorium is active on the current date (same as DOL)
        when(moratoriumService.isMoratoriumActive(currentDate)).thenReturn(true);
        when(moratoriumService.getMoratoriumId()).thenReturn("MOR-2023-10-27-001");
        
        // Given: Validator allows transition but flags moratorium
        StateTransitionValidationResult validationResult = StateTransitionValidationResult.builder()
                .valid(true)
                .moratoriumDetected(true)
                .moratoriumId("MOR-2023-10-27-001")
                .build();
        when(stateTransitionValidator.validateTransition(claimId, ClaimState.OPEN, ClaimState.PROCESSING, currentDate))
                .thenReturn(validationResult);

        // Given: Reserve line creation succeeds
        ReserveLine expectedReserveLine = ReserveLine.builder()
                .reserveId("RES-MOR-001")
                .exposureId("EXP-001")
                .amount(5000.00)
                .currency("USD")
                .approvalStatus(ApprovalStatus.PENDING)
                .build();
        when(reserveLineRepository.save(any(ReserveLine.class))).thenReturn(expectedReserveLine);

        // When: Attempting state transition from OPEN to PROCESSING
        StateTransitionResult result = stateTransitionService.processTransition(
                claimId, 
                ClaimState.OPEN, 
                ClaimState.PROCESSING, 
                currentDate
        );

        // Then: Transition should succeed but reflect moratorium status
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(ClaimState.PROCESSING, result.getNewState());
        assertTrue(result.isMoratoriumApplied());
        assertEquals("MOR-2023-10-27-001", result.getAppliedMoratoriumId());

        // Then: Reserve line should be created/updated with moratorium context
        verify(reserveLineRepository).save(argThat(reserveLine -> 
                reserveLine.getApprovalStatus() == ApprovalStatus.PENDING &&
                reserveLine.getAmount() == 5000.00
        ));

        // Then: Insured engagement should be tracked
        verify(insuredEngagementService).trackEvent(eq(claimId), eq("STATE_TRANSITION"), any());
    }
}
