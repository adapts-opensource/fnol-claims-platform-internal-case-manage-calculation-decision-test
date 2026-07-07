package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Validates decision logic for Multi-Channel FNOL Submission when a catastrophe
 * moratorium begins mid-day of the reported loss.
 * 
 * NFR Compliance:
 * - Input Validation: Verifies boundary handling for temporal constraints.
 * - Idempotency: Ensures idempotency key is processed correctly in decision path.
 * - Observability: Mocked service interactions support structured logging verification.
 */
@ExtendWith(MockitoExtension.class)
public class CatastropheMoratoriumMidDayValidationTest {

    @Mock
    private ValidationDecisionService validationDecisionService;

    @Mock
    private CatastropheMoratoriumService moratoriumService;

    private FnolSubmissionService fnolSubmissionService;

    private static final String TENANT_ID = "newco_insurance";
    private static final String EVENT_ID = "CAT_2023_HURRICANE_ALPHA";
    private static final String IDEMPOTENCY_KEY = "idemp-key-mid-day-test-001";
    private static final String LOSS_DATETIME = "2023-10-27T10:30:00Z";
    private static final String MORATORIUM_START_DATETIME = "2023-10-27T14:00:00Z";

    @BeforeEach
    void setUp() {
        fnolSubmissionService = new FnolSubmissionService(validationDecisionService, moratoriumService);
    }

    @Test
    void catastrophe_moratorium_starts_mid_day_of_loss() {
        // Arrange
        // Scenario: Loss occurs at 10:30 AM, Moratorium starts at 2:00 PM same day.
        // Expected: Submission is allowed because loss predates moratorium start.
        
        // Mocking the moratorium check to return NOT_ACTIVE for the loss timestamp
        when(moratoriumService.isMoratoriumActive(EVENT_ID, LOSS_DATETIME))
            .thenReturn(MoratoriumStatus.NOT_ACTIVE);
        
        // Mocking validation to pass input checks
        when(validationDecisionService.validateInput(any(FnolSubmissionRequest.class)))
            .thenReturn(ValidationResult.pass());

        FnolSubmissionRequest request = new FnolSubmissionRequest();
        request.setTenantId(TENANT_ID);
        request.setEventId(EVENT_ID);
        request.setLossDateTime(LOSS_DATETIME);
        request.setIdempotencyKey(IDEMPOTENCY_KEY);
        request.setChannel("MOBILE_APP");

        // Act
        FnolSubmissionResult result = fnolSubmissionService.submit(request);

        // Assert
        assertEquals(SubmissionStatus.ACCEPTED, result.getStatus(), 
            "Submission should be accepted when loss predates moratorium start");
        assertEquals(IDEMPOTENCY_KEY, result.getIdempotencyKey(), 
            "Idempotency key must be preserved in response");
        assertNotNull(result.getDecision(), "Decision object must be present");
        assertTrue(result.getDecision().isAllowed(), 
            "Decision must allow submission");
        assertTrue(result.getDecision().getReason().contains("LOSS_PRECEDES_MORATORIUM"), 
            "Reason must indicate loss occurred before moratorium");
        
        // Verify interactions to ensure correct flow
        verify(moratoriumService).isMoratoriumActive(EVENT_ID, LOSS_DATETIME);
        verify(validationDecisionService).validateInput(request);
        verifyNoMoreInteractions(moratoriumService, validationDecisionService);
    }
}
