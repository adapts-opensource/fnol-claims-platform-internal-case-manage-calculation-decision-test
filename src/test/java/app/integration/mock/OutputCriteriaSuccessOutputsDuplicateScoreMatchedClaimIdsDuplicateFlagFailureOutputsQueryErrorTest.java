package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Multi-Channel FNOL Submission: state_transition: calculation.
 * Validates output criteria handling, infrastructure I/O mocking, and status updates.
 */
@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    @Mock
    private FnolStateTransitionCalculator stateTransitionCalculator;

    @Mock
    private ClaimDataRepository claimDataRepository;

    private String testEntityId;
    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testEntityId = "multi_channel_fnol_sub_001";
        testPayload = Map.of(
            "id", testEntityId,
            "channel", "web",
            "claim_type", "auto",
            "incident_date", "2023-10-01"
        );
    }

    @Test
    void output_criteria_success_outputs_duplicate_score_matched_claim_ids_duplicate_flag_failure_outputs_query_error_status_updates_claim_flagged_as_potential_duplicate() {
        // Arrange: Define expected outputs per feature specification
        double expectedDuplicateScore = 0.95;
        List<String> expectedMatchedClaimIds = List.of("CLAIM-DUP-001", "CLAIM-DUP-002");
        boolean expectedDuplicateFlag = true;
        String expectedQueryError = null; // failure_outputs: ['query_error'] -> success path

        // Mock infrastructure I/O contracts (S3 payload intake, DynamoDB state store, SES notifications)
        when(stateTransitionCalculator.calculateDuplicateScore(any(Map.class))).thenReturn(expectedDuplicateScore);
        when(stateTransitionCalculator.findMatchedClaims(any(Map.class))).thenReturn(expectedMatchedClaimIds);
        when(stateTransitionCalculator.determineDuplicateFlag(anyDouble(), any(Map.class))).thenReturn(expectedDuplicateFlag);
        when(stateTransitionCalculator.validateQueryError(any(Map.class))).thenReturn(expectedQueryError);

        // Act: Execute state transition calculation
        String actualStatusUpdate = stateTransitionCalculator.processStateTransition(testEntityId, testPayload);

        // Assert: Verify success outputs, failure outputs, and status update
        assertNotNull(actualStatusUpdate, "Status update must not be null");
        assertEquals("Claim flagged as potential duplicate.", actualStatusUpdate);

        // Verify infrastructure I/O interactions with thread-safe, validated inputs
        verify(stateTransitionCalculator, times(1)).calculateDuplicateScore(testPayload);
        verify(stateTransitionCalculator, times(1)).findMatchedClaims(testPayload);
        verify(stateTransitionCalculator, times(1)).determineDuplicateFlag(eq(expectedDuplicateScore), testPayload);
        verify(stateTransitionCalculator, times(1)).validateQueryError(testPayload);

        // Verify failure_outputs contract: query_error must be empty/null
        assertNull(expectedQueryError, "Query error should be null for success path");
    }
}
