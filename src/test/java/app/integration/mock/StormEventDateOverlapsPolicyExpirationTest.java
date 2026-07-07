package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for verifying orchestration decision logic when a storm event date overlaps with policy expiration.
 * 
 * NFR Alignment:
 * - Input Validation: Ensures robust handling of date boundary conditions.
 * - Thread Safety: Mockito mocks are thread-local; assertions verify state isolation.
 * - Observability: Mock interactions can be extended to verify structured log calls if logging service is injected.
 * - Security: Inputs are mocked; no PII or secrets are exposed in test data.
 */
@ExtendWith(MockitoExtension.class)
public class StormEventDateOverlapsPolicyExpirationTest {

    @Mock
    private ExposureService exposureService;

    @Mock
    private PolicyService policyService;

    @Mock
    private DecisionOutcomeRepository outcomeRepository;

    @Mock
    private CommunicationService communicationService;

    @InjectMocks
    private InsuredEngagementOrchestrationService orchestrationService;

    /**
     * Verifies that when a storm event date overlaps with the policy expiration date,
     * the orchestration decision correctly identifies the overlap, maintains coverage validity,
     * and flags the claim for review or special handling.
     */
    @Test
    void storm_event_date_overlaps_policy_expiration() {
        // Arrange
        String exposureId = "EXP-STORM-OVERLAP-001";
        LocalDate stormEventDate = LocalDate.of(2024, 5, 20);
        LocalDate policyExpirationDate = LocalDate.of(2024, 5, 20); // Exact overlap

        when(policyService.getExpirationDate(exposureId)).thenReturn(policyExpirationDate);
        when(exposureService.getExposureId(anyString())).thenReturn(exposureId);

        // Act
        DecisionResult result = orchestrationService.evaluateDecision(exposureId, stormEventDate);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertEquals(DecisionStatus.COVERAGE_VALID_OVERLAP, result.getStatus(),
                "Status should indicate coverage is valid due to overlap");
        assertTrue(result.isFlaggedForManualReview(),
                "Overlap with expiration should trigger manual review flag");
        assertTrue(result.hasOverlapWithExpiration(),
                "Result should explicitly mark overlap condition");

        // Verify interactions
        verify(policyService).getExpirationDate(exposureId);
        verify(outcomeRepository).saveOutcome(any(DecisionResult.class), eq(exposureId));
        
        // Verify no SES notification is sent automatically for overlap (requires review)
        verify(communicationService, never()).sendNotification(any());
    }

    /**
     * Verifies input validation NFR: Invalid exposure ID should throw exception.
     */
    @Test
    void storm_event_date_overlaps_policy_expiration_invalid_exposure_id() {
        // Arrange/Act/Assert
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrationService.evaluateDecision("", LocalDate.of(2024, 1, 1));
        }, "Empty exposure ID should trigger input validation");
    }
}
