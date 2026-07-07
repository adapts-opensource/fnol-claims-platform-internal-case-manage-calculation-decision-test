package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolDecisionValidatorMockTest {

    @Mock
    private PolicyDatabaseClient policyDatabaseClient;

    @Mock
    private MatchingRuleEngine matchingRuleEngine;

    @InjectMocks
    private FnolSubmissionDecisionValidator validator;

    @BeforeEach
    void setUp() {
        // MockitoExtension automatically initializes mocks and injects them into the SUT
    }

    @Test
    void description_validates_required_fields_normalizes_address_and_date_formats_queries_policy_database_applies_matching_rules_and_returns_match_status_with_confidence_score() {
        // Given: Prepare submission payload with required fields
        Map<String, Object> submissionPayload = new HashMap<>();
        submissionPayload.put("id", "fnol-req-001");
        submissionPayload.put("policyId", "POL-TEST-123");
        submissionPayload.put("claimDate", "2023-11-05");
        submissionPayload.put("address", "123 Test Ave, Mockville, CA 90210");

        // Given: Mock policy database response
        Map<String, Object> mockPolicyData = new HashMap<>();
        mockPolicyData.put("policyId", "POL-TEST-123");
        mockPolicyData.put("coverageType", "AUTO");
        mockPolicyData.put("status", "ACTIVE");

        // Given: Mock matching rule engine response
        Map<String, Object> mockDecision = new HashMap<>();
        mockDecision.put("matchStatus", "MATCHED");
        mockDecision.put("confidenceScore", 0.92);

        when(policyDatabaseClient.queryPolicy("POL-TEST-123")).thenReturn(mockPolicyData);
        when(matchingRuleEngine.applyRules(anyMap(), anyMap())).thenReturn(mockDecision);

        // When: Execute validation and decision logic
        Map<String, Object> result = validator.validateAndDecide(submissionPayload);

        // Then: Verify interactions and assertions
        assertNotNull(result, "Decision result should not be null");
        assertEquals("MATCHED", result.get("matchStatus"), "Match status should be MATCHED");
        assertEquals(0.92, result.get("confidenceScore"), "Confidence score should match expected value");

        verify(policyDatabaseClient).queryPolicy("POL-TEST-123");
        verify(matchingRuleEngine).applyRules(anyMap(), anyMap());
        verifyNoMoreInteractions(policyDatabaseClient, matchingRuleEngine);
    }

    // Supporting interfaces for mocking external I/O
    public interface PolicyDatabaseClient {
        Map<String, Object> queryPolicy(String policyId);
    }

    public interface MatchingRuleEngine {
        Map<String, Object> applyRules(Map<String, Object> policyData, Map<String, Object> submissionData);
    }

    // Service Under Test (SUT)
    public static class FnolSubmissionDecisionValidator {
        private final PolicyDatabaseClient policyDatabaseClient;
        private final MatchingRuleEngine matchingRuleEngine;

        public FnolSubmissionDecisionValidator(PolicyDatabaseClient policyDatabaseClient, MatchingRuleEngine matchingRuleEngine) {
            this.policyDatabaseClient = policyDatabaseClient;
            this.matchingRuleEngine = matchingRuleEngine;
        }

        public Map<String, Object> validateAndDecide(Map<String, Object> submission) {
            // 1. Validate required fields
            if (submission == null || submission.get("policyId") == null) {
                throw new IllegalArgumentException("Missing required fields: policyId");
            }

            // 2. Normalize address and date formats
            Map<String, Object> normalized = new HashMap<>(submission);
            if (normalized.get("claimDate") instanceof String) {
                normalized.put("claimDate", LocalDate.parse((String) normalized.get("claimDate")));
            }
            if (normalized.get("address") instanceof String) {
                normalized.put("address", ((String) normalized.get("address")).trim().replaceAll("\\s+", " "));
            }

            // 3. Query policy database
            Map<String, Object> policyData = policyDatabaseClient.queryPolicy((String) normalized.get("policyId"));

            // 4. Apply matching rules
            return matchingRuleEngine.applyRules(policyData, normalized);
        }
    }
}
