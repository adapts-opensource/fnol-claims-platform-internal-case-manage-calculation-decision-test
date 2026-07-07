package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DescriptionComparesCurrentFnolAgainstExistingClaimsUsingMockTest {

    @Mock
    private S3Client auditDiaryS3Client;

    @Mock
    private DynamoDbClient rulesEngineDynamoDbClient;

    @Mock
    private DynamoDbClient workflowTaskRouterDynamoDbClient;

    @InjectMocks
    private ClaimDataStandardizationCalculationTransformService transformService;

    private Map<String, Object> currentFnolPayload;

    @BeforeEach
    void setUp() {
        currentFnolPayload = Map.of(
            "policyNumber", "POL-123456",
            "riskAddress", "123 Main St, Springfield",
            "dateOfLoss", "2023-10-01",
            "causeOfLoss", "Storm",
            "catastropheEvent", "CAT-2023-001",
            "reporter", "John Doe",
            "damagedArea", "Roof",
            "priorClaimStatus", "Open"
        );
    }

    @Test
    void description_compares_current_fnol_against_existing_claims_using_weighted_criteria_policy_number_risk_address_date_of_loss_cause_of_loss_catastrophe_event_reporter_damaged_area_and_prior_claim_status_returns_duplicate_probability_and_linked_claim_id() {
        // Arrange
        double expectedProbability = 0.92;
        String expectedLinkedClaimId = "CLM-EXIST-001";

        Map<String, Object> existingClaimPayload = Map.of(
            "id", expectedLinkedClaimId,
            "policyNumber", "POL-123456",
            "riskAddress", "123 Main St, Springfield",
            "dateOfLoss", "2023-09-28",
            "causeOfLoss", "Storm",
            "catastropheEvent", "CAT-2023-001",
            "reporter", "John Doe",
            "damagedArea", "Roof",
            "priorClaimStatus", "Open"
        );

        when(rulesEngineDynamoDbClient.getItem(any()))
            .thenReturn(Map.of("item", existingClaimPayload));

        when(auditDiaryS3Client.putObject(any(), any()))
            .thenReturn(Map.of("versionId", "v1"));

        // Act
        Map<String, Object> result = transformService.calculateTransformation(currentFnolPayload);

        // Assert
        assertNotNull(result, "Transformation result should not be null");
        assertEquals(expectedProbability, (double) result.get("duplicateProbability"), 0.001, "Duplicate probability should match weighted criteria score");
        assertEquals(expectedLinkedClaimId, result.get("linkedClaimId"), "Linked claim ID should match existing claim");
        assertEquals("DUPLICATE_DETECTED", result.get("status"), "Status should reflect duplicate detection");

        // Verify
        verify(rulesEngineDynamoDbClient, times(1)).getItem(any());
        verify(auditDiaryS3Client, times(1)).putObject(any(), any());
        verify(transformService, times(1)).calculateTransformation(currentFnolPayload);
    }
}
