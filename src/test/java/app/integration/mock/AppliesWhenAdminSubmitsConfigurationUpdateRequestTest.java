package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClaimDataStandardizationStateTransitionOrchestrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimDataStandardizationStateTransitionOrchestrationTest.class);

    @Mock
    private ClaimDataStandardizationStateTransitionOrchService orchestrationService;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    private String baseUri;
    private String testId;
    private Map<String, Object> expectedPayload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        baseUri = System.getenv("APP_BASE_URL");
        if (baseUri == null) {
            baseUri = "http://localhost:8080";
        }
        testId = UUID.randomUUID().toString();
        expectedPayload = Map.of(
            "state", "CONFIG_UPDATE_IN_PROGRESS",
            "version", "1.0",
            "initiatedBy", "ADMIN"
        );
    }

    @Test
    void applies_when_admin_submits_configuration_update_request() {
        // Arrange: Admin submits configuration update request
        String adminRequestPayload = "{\"configKey\":\"CLAIM_STANDARDIZATION_RULES\",\"action\":\"UPDATE\",\"version\":\"1.0\"}";
        assertNotNull(adminRequestPayload, "Input validation: payload must not be null");

        when(orchestrationService.submitConfigurationUpdate(eq(testId), eq(adminRequestPayload)))
                .thenReturn(Map.of("id", testId, "payload", expectedPayload));

        when(dynamoDbClient.putItem(any())).thenReturn(Map.of("status", "SUCCESS"));
        when(s3Client.putObject(any(), any())).thenReturn(Map.of("uri", "s3://document-bucket/orch/" + testId + ".json"));

        LOG.info("Structured logging: Initiating state transition orchestration for id={}", testId);

        // Act: Execute orchestration
        Map<String, Object> response = orchestrationService.submitConfigurationUpdate(testId, adminRequestPayload);

        // Assert: Verify state transition and payload standardization
        assertNotNull(response, "Orchestration response must not be null");
        assertEquals(testId, response.get("id"), "Claim data standardization ID must match");
        assertNotNull(response.get("payload"), "Standardized payload must be returned");
        assertTrue(((Map<?, ?>) response.get("payload")).containsKey("state"), "Payload must contain state field");
        assertEquals("CONFIG_UPDATE_IN_PROGRESS", response.get("payload").get("state"));

        // Verify infrastructure I/O contracts
        verify(orchestrationService, times(1)).submitConfigurationUpdate(testId, adminRequestPayload);
        verify(dynamoDbClient, times(1)).putItem(any());
        verify(s3Client, times(1)).putObject(any(), any());
    }
}
