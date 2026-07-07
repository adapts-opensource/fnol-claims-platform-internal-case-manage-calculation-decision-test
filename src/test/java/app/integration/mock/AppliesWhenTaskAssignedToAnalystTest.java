package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationEnrichmentValidationTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private ClaimDataStandardizationEnrichmentValidationService service;

    @Test
    void applies_when_task_assigned_to_analyst() {
        // Arrange: Simulate claim data payload with task assigned to analyst
        String claimId = "claim-analyst-001";
        Map<String, Object> payload = Map.of(
            "taskId", "task-001",
            "assignedRole", "ANALYST",
            "claimStatus", "PENDING_ENRICHMENT",
            "piiFlags", Map.of("ssn", false, "dob", false)
        );

        // Mock S3 I/O: Document & Media Store contract
        when(s3Client.putObject(anyString(), anyString(), any())).thenReturn("s3://doc-media-store/claim-analyst-001.json");

        // Mock DynamoDB I/O: Policy & Claim Data Store contract
        when(dynamoDbClient.putItem(anyString(), any())).thenReturn(Map.of("validationStatus", "PASSED"));

        // Act: Trigger standardization enrichment & validation
        Map<String, Object> result = service.validateAndEnrich(claimId, payload);

        // Assert: Verify enrichment/validation applies when task is assigned to analyst
        assertNotNull(result);
        assertEquals("PASSED", result.get("validationStatus"));
        assertTrue(result.containsKey("enrichmentTimestamp"));
        assertFalse((Boolean) result.get("piiFlags").get("ssn"));
        verify(s3Client, times(1)).putObject(anyString(), anyString(), any());
        verify(dynamoDbClient, times(1)).putItem(anyString(), any());
    }
}
