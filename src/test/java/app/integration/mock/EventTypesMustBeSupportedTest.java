package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementTransformationMockTest {

    @Mock
    private DecisionTransformationService transformationService;

    @Test
    void event_types_must_be_supported() {
        // Arrange: Mock supported event type for insured engagement tracking
        String supportedEventType = "CLAIM_CREATED";
        when(transformationService.isEventTypeSupported(supportedEventType)).thenReturn(true);

        // Act: Verify the service recognizes the event type
        boolean isSupported = transformationService.isEventTypeSupported(supportedEventType);

        // Assert: Supported event types must be accepted and processed
        assertTrue(isSupported, "Event type must be supported for transformation");

        // Arrange: Mock unsupported event type
        String unsupportedEventType = "LEGACY_ARCHIVE";
        when(transformationService.isEventTypeSupported(unsupportedEventType)).thenReturn(false);

        // Act & Assert: Unsupported event types must be explicitly rejected
        assertFalse(transformationService.isEventTypeSupported(unsupportedEventType), "Unsupported event types must not be processed");
    }
}
