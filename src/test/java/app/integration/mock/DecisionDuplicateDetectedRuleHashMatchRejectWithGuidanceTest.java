package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private SesClient sesClient;
    @Mock
    private DynamoDbClient dynamoDbClient;

    private FnolValidationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new FnolValidationOrchestrator(s3Client, sesClient, dynamoDbClient);
    }

    @Test
    void decisionDuplicateDetectedRuleHashMatchRejectWithGuidanceElseProceedExpectedOutcomeDuplicateResolutionOrIntakeContinuation() {
        // Given: FNOL payload with hash indicating potential duplicate
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "channel", "WEB_PORTAL",
                "vehicleVin", "1HGBH41JXMN109186",
                "incidentHash", "hash_match_value"
            )
        );

        // Mock DynamoDB: returns existing record with matching hash
        when(dynamoDbClient.getItem(anyString(), anyString()))
            .thenReturn(Map.of("pk", claimId, "hash", "hash_match_value"));

        // When: Orchestration validation executes
        ValidationResult outcome = orchestrator.validateAndOrchestrate(payload);

        // Then: Rule triggers hash_match -> reject with guidance
        assertEquals(ValidationStatus.REJECTED_WITH_GUIDANCE, outcome.status());
        assertNotNull(outcome.guidance());
        assertTrue(outcome.guidance().toLowerCase().contains("duplicate"));
        assertEquals(claimId, outcome.correlationId());

        // Verify infra I/O contracts are mocked and called correctly
        verify(dynamoDbClient).getItem(anyString(), anyString());
        verify(s3Client).putObject(any(), any());
        verify(sesClient).sendEmail(any());
    }
}
