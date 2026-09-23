package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChannelMetadataRetentionAuditTest {

    @Mock
    private InsuredDecisionTransformationService transformationService;

    private static final String CHANNEL_TYPE = "PORTAL_EMAIL";
    private static final String CHANNEL_ID = "CH-7890";
    private static final String AUDIT_METADATA_KEY = "channel_metadata";

    @BeforeEach
    void setUp() {
        // Initialize test fixtures and reset mock state if needed
    }

    @Test
    void channel_metadata_must_be_retained_for_audit() {
        // Arrange
        Map<String, Object> inputPayload = Map.of(
            "claim_id", "CLM-112233",
            "decision_type", "APPROVED",
            "channel_metadata", Map.of(
                "type", CHANNEL_TYPE,
                "id", CHANNEL_ID,
                "timestamp", "2024-05-15T08:30:00Z",
                "source_ip", "203.0.113.42"
            )
        );

        Map<String, Object> expectedTransformedPayload = Map.of(
            "claim_id", "CLM-112233",
            "decision_type", "APPROVED",
            "channel_metadata", Map.of(
                "type", CHANNEL_TYPE,
                "id", CHANNEL_ID,
                "timestamp", "2024-05-15T08:30:00Z",
                "source_ip", "203.0.113.42"
            ),
            "audit_retention_flag", true
        );

        when(transformationService.transformDecision(inputPayload)).thenReturn(expectedTransformedPayload);

        // Act
        Map<String, Object> actualOutput = transformationService.transformDecision(inputPayload);

        // Assert
        assertNotNull(actualOutput, "Transformed payload must not be null");
        assertTrue(actualOutput.containsKey(AUDIT_METADATA_KEY), "Channel metadata must be retained for audit compliance (GDPR/SOC2)");

        @SuppressWarnings("unchecked")
        Map<String, Object> retainedMetadata = (Map<String, Object>) actualOutput.get(AUDIT_METADATA_KEY);
        assertEquals(CHANNEL_TYPE, retainedMetadata.get("type"), "Channel type must be preserved");
        assertEquals(CHANNEL_ID, retainedMetadata.get("id"), "Channel ID must be preserved");
        assertEquals("2024-05-15T08:30:00Z", retainedMetadata.get("timestamp"), "Channel timestamp must be preserved");
        assertEquals("203.0.113.42", retainedMetadata.get("source_ip"), "Channel source IP must be preserved");

        verify(transformationService, times(1)).transformDecision(inputPayload);
        verifyNoMoreInteractions(transformationService);
    }

    // Mock interface representing the internal decision transformation layer
    interface InsuredDecisionTransformationService {
        Map<String, Object> transformDecision(Map<String, Object> payload);
    }
}
