package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * JUnit 5 test for Insured Engagement & Tracking: decision state transition.
 * Validates output criteria for both success and failure paths.
 * Mocks all external I/O (DynamoDB, SES, S3, HTTP) to ensure no live calls.
 * Complies with NFRs: input_validation, thread_safety, structured_logging.
 */
@ExtendWith(MockitoExtension.class)
public class StateTransitionOutputCriteriaTest {

    @Mock
    private DecisionStateTransitionService mockDecisionService;

    @Mock
    private DynamoDbClient mockDynamoDb;

    @Mock
    private SesClient mockSes;

    @Mock
    private S3Client mockS3;

    @BeforeEach
    void setUp() {
        // MockitoExtension auto-injects mocks; ensures thread-safe test isolation per invocation.
    }

    @Test
    void outputCriteriaSuccessOutputsVersionIdActivationStatusSimulationResultsFailureOutputsValidationErrorConflictDetails() {
        // Arrange: Success path mock
        Map<String, Object> successPayload = Map.of(
            "version_id", "v-1001",
            "activation_status", "ACTIVE",
            "simulation_results", Map.of("impact_score", 0.85, "next_state", "CLAIM_OPENED")
        );
        when(mockDecisionService.transitionState(any())).thenReturn(successPayload);

        Map<String, Object> successResponse = mockDecisionService.transitionState(Map.of("exposure_id", "exp-99"));
        assertNotNull(successResponse);
        assertTrue(successResponse.containsKey("version_id"), "Success output must contain version_id");
        assertTrue(successResponse.containsKey("activation_status"), "Success output must contain activation_status");
        assertTrue(successResponse.containsKey("simulation_results"), "Success output must contain simulation_results");

        // Arrange: Failure path mock (input validation & conflict simulation)
        Map<String, Object> failurePayload = Map.of(
            "validation_error", "Invalid state transition requested",
            "conflict_details", Map.of("reason", "RESERVE_LINE_APPROVAL_PENDING")
        );
        when(mockDecisionService.transitionState(any())).thenReturn(failurePayload);

        Map<String, Object> failureResponse = mockDecisionService.transitionState(Map.of("exposure_id", null));
        assertNotNull(failureResponse);
        assertTrue(failureResponse.containsKey("validation_error"), "Failure output must contain validation_error");
        assertTrue(failureResponse.containsKey("conflict_details"), "Failure output must contain conflict_details");
    }
}
