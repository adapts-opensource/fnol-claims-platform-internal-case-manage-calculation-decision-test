package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionStateTransitionCalculationMockTest {

    @Mock
    private ClaimsDbClient claimsDbClient;

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    @InjectMocks
    private MultiChannelFnolSubmissionService fnolSubmissionService;

    private static final String SUBMISSION_ID = "fnol-sub-001";
    private static final String INITIAL_STATE = "SUBMITTED";
    private static final String MANUAL_REVIEW_STATE = "MANUAL_REVIEW";

    @BeforeEach
    void setUp() {
        // Reset mocks and prepare test context for each test method
    }

    @Test
    void claims_db_unavailable_retry_fallback_to_manual_review() {
        // Given: Claims DB is temporarily unavailable, throwing an exception on the first attempt
        when(claimsDbClient.fetchSubmissionPayload(SUBMISSION_ID))
                .thenThrow(new RuntimeException("DB connection refused"))
                .thenReturn(Map.of("id", SUBMISSION_ID, "state", INITIAL_STATE));

        // When: State transition calculation is invoked with built-in retry logic
        String finalState = fnolSubmissionService.calculateStateTransition(SUBMISSION_ID);

        // Then: Verify retry mechanism triggered, fallback to manual review executed, and final state is correct
        assertEquals(MANUAL_REVIEW_STATE, finalState);
        verify(claimsDbClient, times(2)).fetchSubmissionPayload(SUBMISSION_ID);
        verify(stateTransitionCalculator, times(1)).applyFallbackToManualReview(SUBMISSION_ID);
    }
}
