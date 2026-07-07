package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

public class MultiChannelFnolSubmissionDecisionValidationMockTest {

    @Mock
    private PolicyCoverageValidator policyCoverageValidator;

    @InjectMocks
    private DecisionValidationEngine decisionValidationEngine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Policy match returns deterministic result within 2s")
    void policy_match_returns_deterministic_result_within_2s() {
        // Arrange: Prepare deterministic input payload matching the entity model
        Map<String, Object> submissionPayload = new HashMap<>();
        submissionPayload.put("id", "fnol-7a8b9c");
        submissionPayload.put("policyNumber", "POL-DETERMINISTIC-001");
        submissionPayload.put("channel", "MOBILE");
        submissionPayload.put("incidentDate", "2024-05-10");

        Map<String, Object> expectedPolicyMatch = new HashMap<>();
        expectedPolicyMatch.put("policyId", "POL-DETERMINISTIC-001");
        expectedPolicyMatch.put("status", "ACTIVE");
        expectedPolicyMatch.put("coverageType", "AUTO");
        expectedPolicyMatch.put("effectiveDate", "2024-01-01");
        expectedPolicyMatch.put("expiryDate", "2025-01-01");

        // Mock DynamoDB-backed policy coverage validator to return consistent result
        when(policyCoverageValidator.matchPolicy(anyMap())).thenReturn(expectedPolicyMatch);

        // Act: Execute decision validation and capture timing
        Instant start = Instant.now();
        Map<String, Object> validationResult = decisionValidationEngine.processDecisionValidation(submissionPayload);
        Instant end = Instant.now();

        // Assert: Verify deterministic output and SLO compliance
        assertNotNull(validationResult, "Validation result must not be null");
        assertEquals(expectedPolicyMatch, validationResult, "Policy match must return identical result across executions");
        assertTrue(Duration.between(start, end).toMillis() <= 2000, 
                "Policy match must complete within the 2-second SLO");
    }
}
