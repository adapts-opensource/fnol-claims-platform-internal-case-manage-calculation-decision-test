package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchMockTest {

    @Mock
    private DynamoDbIntegration dynamoDbIntegration;

    @Mock
    private S3Integration s3Integration;

    @InjectMocks
    private ClaimDataStandardizationStateTransitionOrchestrator orchestrator;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    private static final String CLAIM_ID = "claim-123";
    private static final String TABLE_NAME = "Claim Data Store_table";
    private static final String PK = "pk";
    private static final String BUCKET_NAME = "Document Management-bucket";

    @BeforeEach
    void setUp() {
        lenient().when(dynamoDbIntegration.putItem(any(), any())).thenReturn(Map.of("id", CLAIM_ID, "payload", Map.of()));
        lenient().when(s3Integration.putObject(any(), any())).thenReturn("s3://Document%20Management-bucket/claim-123.json");
    }

    @Test
    void applies_when_agent_portal_fnol_submission_is_received() {
        // Arrange: Simulate Agent Portal FNOL submission payload
        Map<String, Object> fnolPayload = Map.of(
            "claimId", CLAIM_ID,
            "submissionChannel", "AGENT_PORTAL",
            "status", "SUBMITTED",
            "details", Map.of("dateOfLoss", "2024-01-15")
        );

        // Act: Trigger orchestration
        orchestrator.processFnolSubmission(CLAIM_ID, fnolPayload);

        // Assert: Verify state transition orchestration calls
        verify(dynamoDbIntegration, times(1)).putItem(eq(TABLE_NAME), payloadCaptor.capture());
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertNotNull(capturedPayload);
        assertEquals(CLAIM_ID, capturedPayload.get("claimId"));
        assertEquals("SUBMITTED", capturedPayload.get("status"));

        verify(s3Integration, times(1)).putObject(eq(BUCKET_NAME), eq("claim-123.json"));
        assertTrue(capturedPayload.containsKey("payload"));
    }
}
