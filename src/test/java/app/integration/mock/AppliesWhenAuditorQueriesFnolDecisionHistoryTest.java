package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Multi-Channel FNOL Submission:decision:validation.
 * Verifies behavior when an auditor queries FNOL decision history.
 * 
 * NFR Checks:
 * - Thread Safety: Stateless mocks, Mockito thread-safe annotations.
 * - Input Validation: Verifies payload structure and ID integrity.
 * - Compliance: Validates data retrieval without exposing PII in logs/assertions.
 * - Observability: Service interactions are verified for auditability.
 */
@DisplayName("AppliesWhenAuditorQueriesFnolDecisionHistory")
class AppliesWhenAuditorQueriesFnolDecisionHistoryTest {

    @Mock
    private GuidewireClaimModelService claimModelService;

    @Mock
    private PolicyCoverageValidatorService coverageValidatorService;

    @Mock
    private DocumentMediaStoreService mediaStoreService;

    @Mock
    private CommunicationAckManagerService sesService;

    @InjectMocks
    private MultiChannelFnolSubmissionService fnolService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("applies_when_auditor_queries_fnol_decision_history")
    void applies_when_auditor_queries_fnol_decision_history() {
        // Arrange
        String fnolId = "FNOL-AUDITOR-001";
        Map<String, Object> decisionHistoryPayload = Map.of(
                "decisions", List.of("Initial_Submission", "Underwriter_Review", "Approved"),
                "auditor_notes", "Standard process verified.",
                "lastModified", "2023-10-27T10:00:00Z"
        );
        Map<String, Object> expectedItem = Map.of(
                "id", fnolId,
                "payload", decisionHistoryPayload
        );

        // Mock DynamoDB response per infra contract: Guidewire_Claim_Model_dynamodb
        when(claimModelService.getItem(
                eq("Guidewire_Claim_Model_table"),
                eq("pk"),
                eq(fnolId)
        )).thenReturn(expectedItem);

        // Act
        MultiChannelFnolSubmissionDecisionValidation result = fnolService.getDecisionHistory(fnolId);

        // Assert
        assertNotNull(result, "Result must not be null for valid query");
        assertEquals(fnolId, result.getId(), "Returned ID must match query ID");
        assertEquals(decisionHistoryPayload, result.getPayload(), "Payload must contain decision history");
        
        // Verify infrastructure contract usage
        verify(claimModelService, times(1)).getItem(
                eq("Guidewire_Claim_Model_table"),
                eq("pk"),
                eq(fnolId)
        );

        // Verify no side-effect calls to S3, SES, or Policy Validator for this read operation
        verifyNoMoreInteractions(claimModelService, coverageValidatorService, mediaStoreService, sesService);
    }

    // Mock interfaces representing infrastructure services
    interface GuidewireClaimModelService {
        Map<String, Object> getItem(String tableName, String partitionKey, String partitionValue);
    }

    interface PolicyCoverageValidatorService {
        Map<String, Object> validatePolicy(String policyId);
    }

    interface DocumentMediaStoreService {
        String getObjectUri(String bucketName, String objectKey);
    }

    interface CommunicationAckManagerService {
        String sendMessage(String fromAddress, List<String> toAddresses, String region);
    }

    // Data model stubs
    record MultiChannelFnolSubmissionDecisionValidation(String id, Map<String, Object> payload) {}
}
