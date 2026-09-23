package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Validates Claim Data Standardization:enrichment:validation for the scenario where
 * an agent submits a First Notice of Loss (FNOL) via the customer portal.
 * Mocks external I/O (S3, DynamoDB) to ensure no live AWS calls during test execution.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationEnrichmentValidationTest {

    @Mock
    private ClaimEnrichmentValidationService validationService;

    @Mock
    private DocumentMediaStoreClient s3Client;

    @Mock
    private PolicyClaimDataStoreClient dynamoDbClient;

    @InjectMocks
    private ClaimSubmissionOrchestrator submissionOrchestrator;

    @Test
    void appliesWhenAgentSubmitsFnolViaPortal() {
        // Arrange: Simulate agent portal submission context
        String agentId = "agent-123";
        String portalSessionId = "portal-session-456";
        Map<String, Object> rawFnolPayload = Map.of(
                "claimType", "AUTO",
                "incidentDate", "2023-10-25",
                "description", "Rear-end collision"
        );

        String expectedClaimId = UUID.randomUUID().toString();
        Map<String, Object> enrichedPayload = Map.of(
                "id", expectedClaimId,
                "claimType", "AUTO",
                "incidentDate", "2023-10-25T00:00:00Z",
                "description", "Rear-end collision",
                "submittedVia", "PORTAL",
                "agentId", agentId,
                "standardizationStatus", "VALIDATED",
                "enrichmentTimestamp", "2023-10-25T12:00:00Z"
        );

        // Mock external I/O contracts (S3 & DynamoDB) and validation logic
        when(validationService.validateAndEnrich(rawFnolPayload)).thenReturn(enrichedPayload);
        when(s3Client.putObject(any(), any(), any())).thenReturn("s3://doc-store/" + expectedClaimId + ".json");
        when(dynamoDbClient.putItem(any(), any())).thenReturn(Map.of("Item", enrichedPayload));

        // Act: Process FNOL submission through the orchestrator
        Map<String, Object> result = submissionOrchestrator.processFnolSubmission(agentId, portalSessionId, rawFnolPayload);

        // Assert: Verify enrichment, validation, and data model compliance
        assertNotNull(result, "Result payload must not be null after processing");
        assertEquals("VALIDATED", result.get("standardizationStatus"), "Payload must pass validation rules");
        assertEquals("PORTAL", result.get("submittedVia"), "Submission channel must be recorded");
        assertEquals(agentId, result.get("agentId"), "Agent context must be enriched from portal session");
        assertTrue(result.containsKey("enrichmentTimestamp"), "Enrichment metadata must be present per NFR observability");
        assertEquals(expectedClaimId, result.get("id"), "Data model 'id' field must be populated");

        // Verify infra I/O contracts are invoked exactly once with expected parameters
        verify(validationService, times(1)).validateAndEnrich(rawFnolPayload);
        verify(s3Client, times(1)).putObject(eq("Document & Media Store-bucket"), eq("Document & Media Store/" + expectedClaimId + ".json"), any());
        verify(dynamoDbClient, times(1)).putItem(eq("Policy & Claim Data Store_table"), any());
    }
}
