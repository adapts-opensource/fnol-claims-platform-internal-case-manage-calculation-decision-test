package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporterIdMismatchTest {

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    @Test
    void reporter_id_mismatch() {
        // given
        String claimId = "CLM-77492";
        String invalidReporterId = "RPT-UNAUTHORIZED-001";
        String targetState = "CLAIM_OPENED";

        // Mock external repository to simulate reporter ID validation failure during state transition
        when(stateTransitionRepository.validateReporterForStateTransition(claimId, invalidReporterId, targetState))
                .thenThrow(new IllegalArgumentException("Reporter ID mismatch: expected authorized reporter, got " + invalidReporterId));

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                insuredEngagementService.processStateTransition(claimId, targetState, invalidReporterId)
        );
    }
}
