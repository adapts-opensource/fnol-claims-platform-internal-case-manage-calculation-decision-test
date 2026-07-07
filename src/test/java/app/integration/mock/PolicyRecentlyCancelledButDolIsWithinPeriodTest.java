package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    @Mock
    private FnolSubmissionService fnolSubmissionService;

    @InjectMocks
    private FnolSubmissionProcessor fnolSubmissionProcessor;

    @Test
    void policy_recently_cancelled_but_dol_is_within_period() {
        String submissionId = "FNOL-78901";
        Map<String, Object> payload = Map.of(
            "policyId", "POL-445566",
            "policyStatus", "CANCELLED",
            "cancellationDate", LocalDate.now().minusDays(12).format(DateTimeFormatter.ISO_LOCAL_DATE),
            "dateOfLoss", LocalDate.now().minusDays(4).format(DateTimeFormatter.ISO_LOCAL_DATE),
            "channel", "WEB"
        );

        when(stateTransitionCalculator.evaluate(eq(submissionId), any(Map.class)))
            .thenReturn("CLAIM_ACTIVE");

        when(fnolSubmissionService.persistAndTransition(eq(submissionId), eq(payload)))
            .thenReturn(Map.of("state", "CLAIM_ACTIVE", "id", submissionId));

        Map<String, Object> result = fnolSubmissionProcessor.processSubmission(submissionId, payload);

        assertEquals("CLAIM_ACTIVE", result.get("state"));
        assertEquals(submissionId, result.get("id"));
        verify(stateTransitionCalculator).evaluate(eq(submissionId), eq(payload));
        verify(fnolSubmissionService).persistAndTransition(eq(submissionId), eq(payload));
    }
}
