package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * JUnit 5 test class verifying that IDs must exist in the system before
 * transformation decisions are processed.
 */
public class InsuredEngagementTransformationTest {

    @Mock
    private ReserveLineRepository reserveLineRepository;

    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        decisionTransformationService = new DecisionTransformationService(reserveLineRepository);
    }

    @Test
    void ids_must_exist_in_system() {
        // Arrange: Mock repository to simulate IDs existing in the system
        String reserveId = "reserve-001";
        String exposureId = "exposure-002";

        when(reserveLineRepository.findById(reserveId)).thenReturn(Optional.of(createReserveLine(reserveId, exposureId)));
        when(reserveLineRepository.findByExposureId(exposureId)).thenReturn(Optional.of(createReserveLine(reserveId, exposureId)));

        // Act & Assert: Verify transformation proceeds without throwing when IDs exist
        assertDoesNotThrow(() -> decisionTransformationService.transformDecision(reserveId, exposureId));

        // Verify repository calls were made to validate existence before transformation
        verify(reserveLineRepository, times(1)).findById(reserveId);
        verify(reserveLineRepository, times(1)).findByExposureId(exposureId);
    }

    private ReserveLine createReserveLine(String reserveId, String exposureId) {
        ReserveLine reserveLine = new ReserveLine();
        reserveLine.setReserveId(reserveId);
        reserveLine.setExposureId(exposureId);
        reserveLine.setAmount(BigDecimal.valueOf(1000.00));
        reserveLine.setCurrency("USD");
        reserveLine.setApprovalStatus("Pending");
        return reserveLine;
    }
}
