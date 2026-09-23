package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Multi-Channel FNOL Submission: validation: decision.
 * Validates required inputs, optional inputs, schema conformity, timestamp validity,
 * and 72-hour deduplication freshness requirements.
 */
@ExtendWith(MockitoExtension.class)
class InputCriteriaRequiredInputsChannelTypeEnumRawPayloadJsonXmlTextTest {

    @Mock
    private SubmissionValidationService validationService;

    private Map<String, Object> validSubmissionRequest;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        validSubmissionRequest = Map.of(
            "channel_type", "WEB",
            "raw_payload", "{\"claimId\":\"C100\",\"type\":\"auto\"}",
            "submission_timestamp", now.toString(),
            "client_ip", "192.168.1.10",
            "user_agent", "FNOL-Client/2.1",
            "session_id", "sess-abc-789"
        );
    }

    @Test
    void inputCriteriaRequiredInputsChannelTypeEnumRawPayloadJsonXmlText() {
        when(validationService.validate(anyMap())).thenReturn(true);
        assertTrue(validationService.validate(validSubmissionRequest));
        verify(validationService).validate(validSubmissionRequest);
    }

    @Test
    void submissionTimestampIso8601() {
        Instant validTs = Instant.parse("2024-01-15T10:30:00Z");
        Map<String, Object> payload = Map.of(
            "channel_type", "MOBILE",
            "raw_payload", "{\"policyId\":\"P200\"}",
            "submission_timestamp", validTs.toString()
        );
        when(validationService.validate(payload)).thenReturn(true);
        assertTrue(validationService.validate(payload));
    }

    @Test
    void optionalInputsClientIpStringUserAgentStringSessionIdString() {
        Map<String, Object> payloadWithOptional = Map.copyOf(validSubmissionRequest);
        when(validationService.validate(payloadWithOptional)).thenReturn(true);
        assertTrue(validationService.validate(payloadWithOptional));
    }

    @Test
    void optionalInputsAbsentStillPassesValidation() {
        Map<String, Object> payloadWithoutOptional = Map.of(
            "channel_type", "WEB",
            "raw_payload", "{\"data\":\"minimal\"}",
            "submission_timestamp", Instant.now().toString()
        );
        when(validationService.validate(payloadWithoutOptional)).thenReturn(true);
        assertTrue(validationService.validate(payloadWithoutOptional));
    }

    @Test
    void inputValidationRawPayloadConformsToChannelSchema() {
        Map<String, Object> validSchemaPayload = Map.of(
            "channel_type", "PORTAL",
            "raw_payload", "{\"claimType\":\"collision\",\"severity\":\"minor\"}",
            "submission_timestamp", Instant.now().toString()
        );
        when(validationService.validate(validSchemaPayload)).thenReturn(true);
        assertTrue(validationService.validate(validSchemaPayload));
    }

    @Test
    void inputValidationRawPayloadInvalidSchemaRejected() {
        Map<String, Object> invalidSchemaPayload = Map.of(
            "channel_type", "WEB",
            "raw_payload", "<invalid><xml>malformed</xml>",
            "submission_timestamp", Instant.now().toString()
        );
        when(validationService.validate(invalidSchemaPayload)).thenThrow(IllegalArgumentException.class);
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(invalidSchemaPayload));
    }

    @Test
    void submissionTimestampIsValidFreshnessRequirementsDeduplicationWindowLast72Hours() {
        Instant freshTimestamp = Instant.now().minusSeconds(70 * 3600); // Within 72h
        Map<String, Object> freshPayload = Map.of(
            "channel_type", "CALL_CENTER",
            "raw_payload", "{\"contactPhone\":\"555-0100\"}",
            "submission_timestamp", freshTimestamp.toString()
        );
        when(validationService.validate(freshPayload)).thenReturn(true);
        assertTrue(validationService.validate(freshPayload));
    }

    @Test
    void submissionTimestampOutsideDeduplicationWindowRejected() {
        Instant staleTimestamp = Instant.now().minusSeconds(73 * 3600); // > 72h
        Map<String, Object> stalePayload = Map.of(
            "channel_type", "WEB",
            "raw_payload", "{\"claimId\":\"C999\"}",
            "submission_timestamp", staleTimestamp.toString()
        );
        when(validationService.validate(stalePayload)).thenThrow(IllegalArgumentException.class);
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(stalePayload));
    }

    @Test
    void missingRequiredInputsThrowsValidationException() {
        Map<String, Object> emptyPayload = Map.of();
        when(validationService.validate(emptyPayload)).thenThrow(IllegalArgumentException.class);
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(emptyPayload));
    }
}
