package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:transformation:orchestration.
 * Verifies handling path assignment based on policy match, date validation,
 * cause of loss, and regulatory constraints using mocked infrastructure.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private DynamoDbClient rulesClient;

    @Mock
    private DynamoDbClient claimDataClient;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private ClaimDataStandardizationOrchestrationService service;

    @Test
    void purpose_assign_claim_to_appropriate_handling_path_based_on_policy_match_date_validation_cause_of_loss_and_regulatory_constraints() {
        // Arrange
        String claimId = "CLM-TEST-001";
        String policyId = "POL-STD-999";
        LocalDate claimDate = LocalDate.now().minusDays(5);
        String causeOfLoss = "THEFT";
        Set<String> regulatoryConstraints = Set.of("DATA_PRIVACY_HIGH", "STATE_INSURANCE_505");

        // Mock Rules & Triage Service response
        // Simulates policy match found, valid date window, and regulatory override path
        Map<String, AttributeValue> rulePayload = Map.of(
            "policyMatch", AttributeValue.builder().s("TRUE").build(),
            "handlingPath", AttributeValue.builder().s("REGULATORY_HIGH_PRIORITY").build(),
            "maxDateDiff", AttributeValue.builder().s("30").build(),
            "validCauses", AttributeValue.builder().l(
                Set.of("THEFT", "COLLISION").stream()
                    .map(v -> AttributeValue.builder().s(v).build())
                    .toList()
            ).build()
        );
        when(rulesClient.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().item(rulePayload).build());

        // Mock Claim Data Store response
        // Simulates current claim state with payload
        Map<String, AttributeValue> claimPayload = Map.of(
            "policyId", AttributeValue.builder().s(policyId).build(),
            "claimDate", AttributeValue.builder().s(claimDate.toString()).build(),
            "causeOfLoss", AttributeValue.builder().s(causeOfLoss).build(),
            "regulatoryConstraints", AttributeValue.builder().l(
                regulatoryConstraints.stream().map(v -> AttributeValue.builder().s(v).build()).toList()
            ).build()
        );
        Map<String, AttributeValue> claimState = Map.of(
            "pk", AttributeValue.builder().s(claimId).build(),
            "payload", AttributeValue.builder().m(claimPayload).build()
        );
        when(claimDataClient.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().item(claimState).build());

        // Act
        ClaimDataStandardizationStateTransitionOrch result = service.assignHandlingPath(claimId);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals("REGULATORY_HIGH_PRIORITY", result.getPayload().get("assignedHandlingPath"),
            "Handling path should reflect regulatory constraints and policy match");
        
        // Verify infrastructure interactions
        verify(rulesClient, times(1)).getItem(any());
        verify(claimDataClient, times(1)).getItem(any());
        verifyNoInteractions(s3Client, "S3 should not be accessed for path assignment logic");
    }
}
