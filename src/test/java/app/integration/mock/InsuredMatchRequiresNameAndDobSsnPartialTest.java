package app.integration.mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.integration.mock.model.InsuredMatchRecord;
import app.integration.mock.model.StateTransitionResult;
import app.integration.mock.service.InsuredMatchRepository;
import app.integration.mock.service.StateTransitionCalculator;

/**
 * Mock test for Multi-Channel FNOL Submission:state_transition:calculation.
 * Verifies state transition logic against mocked infrastructure contracts.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionStateTransitionCalculationMockTest {

    @Mock
    private InsuredMatchRepository insuredMatchRepository;

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    @InjectMocks
    private app.integration.mock.service.FnolSubmissionStateTransitionService submissionService;

    private static final String SUBMISSION_ID = "fnol-123";
    private static final String INSURED_NAME = "John Doe";
    private static final String INSURED_DOB = "1980-01-01";
    private static final String INSURED_SSN_PARTIAL = "123-45";

    @BeforeEach
    void setUp() {
        // Reset mocks between tests
        clearInvocations(insuredMatchRepository);
        clearInvocations(stateTransitionCalculator);
    }

    @Test
    void insured_match_requires_name_and_dob_ssn_partial_match() {
        // Arrange
        Map<String, Object> payload = Map.of(
            "submissionId", SUBMISSION_ID,
            "insured", Map.of(
                "name", INSURED_NAME,
                "dob", INSURED_DOB,
                "ssn", INSURED_SSN_PARTIAL
            ),
            "channel", "MOBILE_APP"
        );

        // Mock repository to simulate DynamoDB partial match lookup
        InsuredMatchRecord existingRecord = new InsuredMatchRecord(
            "existing-insured-001",
            INSURED_NAME,
            LocalDate.parse(INSURED_DOB),
            "123-45-6789",
            true
        );

        when(insuredMatchRepository.findByPartialCriteria(
            eq(INSURED_NAME),
            eq(LocalDate.parse(INSURED_DOB)),
            eq(INSURED_SSN_PARTIAL)
        )).thenReturn(Optional.of(existingRecord));

        // Mock calculator to simulate state transition logic
        StateTransitionResult expectedResult = StateTransitionResult.builder()
            .stateCode("PARTIAL_MATCH_REVIEW")
            .requiresManualReview(true)
            .matchScore(0.85)
            .message("Insured match requires name and DOB/SSN partial match validation.")
            .build();

        when(stateTransitionCalculator.calculateStateTransition(anyMap())).thenReturn(expectedResult);

        // Act
        StateTransitionResult result = submissionService.processStateTransition(SUBMISSION_ID, payload);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals("PARTIAL_MATCH_REVIEW", result.getStateCode(), "State should transition to PARTIAL_MATCH_REVIEW");
        assertTrue(result.isRequiresManualReview(), "Manual review should be required for partial match");
        assertEquals(0.85, result.getMatchScore(), 0.0, "Match score should be preserved");

        // Verify infrastructure interactions
        verify(insuredMatchRepository).findByPartialCriteria(
            eq(INSURED_NAME),
            eq(LocalDate.parse(INSURED_DOB)),
            eq(INSURED_SSN_PARTIAL)
        );
        
        verify(stateTransitionCalculator).calculateStateTransition(payload);
    }
}
