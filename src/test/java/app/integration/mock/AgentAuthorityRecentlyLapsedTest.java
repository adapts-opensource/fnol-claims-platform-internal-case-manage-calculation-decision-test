package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentAuthorityRecentlyLapsedTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private SesClient sesClient;

    private FnolSubmissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new FnolSubmissionOrchestrator(dynamoDbClient, s3Client, sesClient);
    }

    @Test
    void agent_authority_recently_lapsed() {
        // Given: Payload representing a submission with recently lapsed agent authority
        String submissionId = "fnol-sub-789";
        Map<String, Object> payload = new HashMap<>();
        payload.put("agent_id", "AGT-456");
        payload.put("agent_authority_status", "RECENTLY_LAPSED");
        payload.put("authority_lapse_date", LocalDateTime.now().minusDays(3).toString());
        payload.put("channel", "AGENT_PORTAL");

        // Mock validation failure at the state transition layer
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(new IllegalArgumentException("VALIDATION_ERROR: Agent authority recently lapsed"));

        // When & Then: Orchestration should surface the validation error and skip downstream I/O
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.processSubmission(submissionId, payload);
        });

        assertEquals("VALIDATION_ERROR: Agent authority recently lapsed", thrown.getMessage());

        // Verify state transition attempt and payload structure
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, times(1)).putItem(captor.capture());
        Map<String, AttributeValue> capturedItem = captor.getValue().item();
        assertEquals(submissionId, capturedItem.get("id").s());
        assertEquals("RECENTLY_LAPSED", capturedItem.get("payload").m().get("agent_authority_status").s());

        // Verify downstream channels (SES, S3) were NOT invoked due to validation failure
        verifyNoInteractions(sesClient);
        verifyNoInteractions(s3Client);
    }
}
