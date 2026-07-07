package app.integration.mock;

import app.domain.enums.ApprovalStatus;
import app.domain.enums.ValidationFlag;
import app.domain.model.ReserveLine;
import app.infrastructure.AuditService;
import app.infrastructure.DolValidatorService;
import app.infrastructure.SesCommunicationService;
import app.infrastructure.S3DocumentStoreService;
import app.repository.ReserveLineRepository;
import app.service.InsuredEngagementService;
import app.service.DecisionTransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Insured Engagement & Tracking:decision:transformation.
 * Verifies DOL validation logic flags out-of-period losses correctly.
 */
@ExtendWith(MockitoExtension.class)
public class DolValidationCorrectlyFlagsOutOfPeriodLossesTest {

    @Mock
    private ReserveLineRepository reserveLineRepository;

    @Mock
    private DecisionTransformationService decisionTransformationService;

    @Mock
    private DolValidatorService dolValidatorService;

    @Mock
    private SesCommunicationService sesCommunicationService;

    @Mock
    private S3DocumentStoreService s3DocumentStoreService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    private ReserveLine validReserveLine;
    private ReserveLine outOfPeriodReserveLine;
    private LocalDate currentPolicyDate;
    private LocalDate outOfPeriodDate;

    @BeforeEach
    void setUp() {
        currentPolicyDate = LocalDate.now();
        outOfPeriodDate = currentPolicyDate.minusYears(2);

        validReserveLine = createReserveLine("res-valid", "exp-100", currentPolicyDate, BigDecimal.valueOf(5000));
        outOfPeriodReserveLine = createReserveLine("res-oop", "exp-200", outOfPeriodDate, BigDecimal.valueOf(15000));
    }

    @Test
    void dol_validation_correctly_flags_out_of_period_losses() {
        // Arrange: Mock DOL validation to detect out-of-period loss
        when(dolValidatorService.validateDateOfLoss(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new IllegalArgumentException("Date of Loss is outside policy period"));

        // Arrange: Mock transformation success but expect validation failure handling
        when(decisionTransformationService.transformDecision(any(ReserveLine.class)))
                .thenReturn(outOfPeriodReserveLine);

        // Arrange: Mock audit logging for observability
        doNothing().when(auditService).logEvent(anyString(), anyString(), any(Map.class));

        // Act: Execute transformation and validation
        ReserveLine result = insuredEngagementService.processReserveLine(outOfPeriodReserveLine);

        // Assert: Verify status is updated to Rejected due to validation flag
        assertEquals(ApprovalStatus.REJECTED, result.getApprovalStatus(),
                "Reserve line should be rejected when DOL is out of period");

        // Assert: Verify validation flags are populated
        assertNotNull(result.getValidationFlags(), "Validation flags should not be null");
        assertTrue(result.getValidationFlags().contains(ValidationFlag.DOL_OUT_OF_PERIOD),
                "DOL_OUT_OF_PERIOD flag should be present");

        // Assert: Verify repository interaction with updated status
        verify(reserveLineRepository).save(argThat(savedReserve ->
                savedReserve.getReserveId().equals(outOfPeriodReserveLine.getReserveId()) &&
                savedReserve.getApprovalStatus().equals(ApprovalStatus.REJECTED)
        ));

        // Assert: Verify audit trail for compliance and observability
        verify(auditService, times(1)).logEvent(eq("DECISION_TRANSFORM"), eq("VALIDATION_FAILURE"), any(Map.class));

        // Assert: Verify no SES notification sent for rejected validation errors (security/operability)
        verify(sesCommunicationService, never()).sendNotification(anyString(), anyList(), anyString());
    }

    @Test
    void dol_validation_allows_valid_period_losses() {
        // Arrange: Valid DOL passes validation
        when(dolValidatorService.validateDateOfLoss(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        when(decisionTransformationService.transformDecision(any(ReserveLine.class)))
                .thenReturn(validReserveLine);

        doNothing().when(auditService).logEvent(anyString(), anyString(), any(Map.class));

        // Act
        ReserveLine result = insuredEngagementService.processReserveLine(validReserveLine);

        // Assert
        assertEquals(ApprovalStatus.PENDING, result.getApprovalStatus(),
                "Reserve line should remain pending when DOL is valid");
        assertTrue(result.getValidationFlags().isEmpty(),
                "Validation flags should be empty for valid losses");

        verify(reserveLineRepository).save(argThat(savedReserve ->
                savedReserve.getReserveId().equals(validReserveLine.getReserveId()) &&
                savedReserve.getApprovalStatus().equals(ApprovalStatus.PENDING)
        ));
    }

    private ReserveLine createReserveLine(String reserveId, String exposureId, LocalDate dol, BigDecimal amount) {
        ReserveLine reserveLine = new ReserveLine();
        reserveLine.setReserveId(reserveId);
        reserveLine.setExposureId(exposureId);
        reserveLine.setDol(dol);
        reserveLine.setAmount(amount);
        reserveLine.setCurrency("USD");
        reserveLine.setApprovalStatus(ApprovalStatus.PENDING);
        reserveLine.setValidationFlags(Collections.emptyList());
        return reserveLine;
    }
}
