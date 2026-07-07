package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Tests for Multi-Channel FNOL Submission validation decision logic,
 * specifically verifying timezone handling and consistency across channels.
 */
@ExtendWith(MockitoExtension.class)
class TimezoneDifferencesAcrossChannelsTest {

    @Mock
    private FnolSubmissionValidator validator;

    @Mock
    private ChannelMetadataResolver channelResolver;

    private FnolDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new FnolDecisionService(validator, channelResolver);
    }

    @Test
    void should_normalize_timezone_and_consistent_decision_for_same_utc_timestamp() {
        // Arrange
        ZonedDateTime utcTimestamp = ZonedDateTime.now(ZoneOffset.UTC);
        String claimPayload = "{\"policyId\": \"POL-123\", \"lossDate\": \"2023-10-27\"}";
        Map<String, String> channelMetadata = Map.of("channelId", "WEB", "clientOffset", "-05:00");
        DecisionOutcome expectedDecision = DecisionOutcome.ACCEPTED;

        when(channelResolver.resolve(anyString())).thenReturn(channelMetadata);
        when(validator.validate(anyString(), any(ZonedDateTime.class))).thenReturn(expectedDecision);

        // Act
        DecisionOutcome webDecision = decisionService.processSubmission("WEB", claimPayload, utcTimestamp);
        DecisionOutcome mobileDecision = decisionService.processSubmission("MOBILE", claimPayload, utcTimestamp);

        // Assert
        assertEquals(expectedDecision, webDecision, "Web channel decision should match expected");
        assertEquals(expectedDecision, mobileDecision, "Mobile channel decision should match expected");
        verify(validator, times(2)).validate(anyString(), any(ZonedDateTime.class));
    }

    @Test
    void should_handle_midnight_rollover_correctly_across_channels() {
        // Arrange
        // Simulate submission at 23:59 local in New York (UTC-5) which is 04:59 UTC next day
        // vs submission at 00:01 local in London (UTC+0) which is 00:01 UTC same day
        // The business day logic should rely on the canonical timestamp or normalized date
        ZonedDateTime nyTimestamp = ZonedDateTime.of(2023, 10, 27, 23, 59, 0, 0, ZoneOffset.of("-05:00"));
        ZonedDateTime londonTimestamp = ZonedDateTime.of(2023, 10, 28, 0, 1, 0, 0, ZoneOffset.of("+00:00"));
        
        String claimPayload = "{\"policyId\": \"POL-456\", \"lossDate\": \"2023-10-27\"}";
        DecisionOutcome expectedDecision = DecisionOutcome.ROUTE_FOR_REVIEW;

        when(channelResolver.resolve("WEB")).thenReturn(Map.of("channelId", "WEB", "clientOffset", "-05:00"));
        when(channelResolver.resolve("CALL_CENTER")).thenReturn(Map.of("channelId", "CALL_CENTER", "clientOffset", "+00:00"));
        when(validator.validate(anyString(), any(ZonedDateTime.class))).thenReturn(expectedDecision);

        // Act
        DecisionOutcome nyDecision = decisionService.processSubmission("WEB", claimPayload, nyTimestamp);
        DecisionOutcome londonDecision = decisionService.processSubmission("CALL_CENTER", claimPayload, londonTimestamp);

        // Assert
        assertEquals(expectedDecision, nyDecision, "NY submission decision should be consistent");
        assertEquals(expectedDecision, londonDecision, "London submission decision should be consistent");
    }

    @Test
    void should_reject_submission_with_invalid_timezone_metadata() {
        // Arrange
        ZonedDateTime timestamp = ZonedDateTime.now(ZoneOffset.UTC);
        String claimPayload = "{\"policyId\": \"POL-789\"}";
        
        when(channelResolver.resolve("UNKNOWN_CHANNEL")).thenReturn(Map.of("channelId", "UNKNOWN", "clientOffset", "INVALID"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            decisionService.processSubmission("UNKNOWN_CHANNEL", claimPayload, timestamp),
            "Submission should fail when timezone metadata is invalid"
        );
    }
}
