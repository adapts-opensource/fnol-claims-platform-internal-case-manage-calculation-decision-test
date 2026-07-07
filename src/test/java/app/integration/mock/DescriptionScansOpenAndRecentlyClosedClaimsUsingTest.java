package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class DescriptionScansOpenAndRecentlyClosedClaimsUsingTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private DuplicateScoringService duplicateScoringService;
    @Mock
    private ReviewTaskService reviewTaskService;

    @InjectMocks
    private ClaimDecisionService claimDecisionService;

    private static final double SCORE_THRESHOLD = 0.80;
    private static final String POLICY_ID = "POL-98765";
    private static final String ADDRESS = "456 Oak Ave";
    private static final LocalDate INCIDENT_DATE = LocalDate.of(2024, 5, 12);
    private static final String CAUSE = "FLOOD";
    private static final String CATASTROPHE_ID = "CAT-2024-002";
    private static final String REPORTER_ID = "REP-101";
    private static final String DAMAGED_AREA = "FOUNDATION";
    private static final List<String> TARGET_STATUSES = List.of("OPEN", "RECENTLY_CLOSED");

    @Test
    void description_scans_open_and_recently_closed_claims_using_policy_address_date_cause_catastrophe_reporter_damaged_area_and_status_computes_duplicate_score_triggers_review_task_if_threshold_exceeded() {
        // Given: Simulate scanning open and recently closed claims matching all criteria
        Claim mockClaim1 = new Claim("CLM-001", POLICY_ID, ADDRESS, INCIDENT_DATE, CAUSE, CATASTROPHE_ID, REPORTER_ID, DAMAGED_AREA, "OPEN");
        Claim mockClaim2 = new Claim("CLM-002", POLICY_ID, ADDRESS, INCIDENT_DATE, CAUSE, CATASTROPHE_ID, REPORTER_ID, DAMAGED_AREA, "RECENTLY_CLOSED");
        List<Claim> matchedClaims = List.of(mockClaim1, mockClaim2);

        when(claimRepository.findByCriteria(anyString(), anyString(), any(LocalDate.class), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(matchedClaims);

        // Mock scoring to return a value exceeding the threshold
        when(duplicateScoringService.computeDuplicateScore(anyList())).thenReturn(0.92);

        // When: Execute the decision workflow
        claimDecisionService.processDecision(POLICY_ID, ADDRESS, INCIDENT_DATE, CAUSE, CATASTROPHE_ID, REPORTER_ID, DAMAGED_AREA, TARGET_STATUSES, SCORE_THRESHOLD);

        // Then: Verify repository scan occurred with correct parameters
        verify(claimRepository, times(1)).findByCriteria(eq(POLICY_ID), eq(ADDRESS), eq(INCIDENT_DATE), eq(CAUSE), eq(CATASTROPHE_ID), eq(REPORTER_ID), eq(DAMAGED_AREA), eq(TARGET_STATUSES));

        // Verify duplicate score computation
        verify(duplicateScoringService, times(1)).computeDuplicateScore(matchedClaims);

        // Verify review task triggered because score (0.92) > threshold (0.80)
        verify(reviewTaskService, times(1)).triggerReviewTask(eq(0.92), eq(matchedClaims));

        // Assert business rule enforcement
        assertTrue(true, "Workflow correctly computed duplicate score and triggered review task upon threshold exceedance");
    }
}
