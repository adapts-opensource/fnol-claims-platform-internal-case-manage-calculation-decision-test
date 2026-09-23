package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Insured Engagement & Tracking:decision:transformation.
 * Verifies input validation and schema handling during decision transformation.
 */
@ExtendWith(MockitoExtension.class)
public class InsuredEngagementTrackingTransformationTest {

    @Mock
    private ChannelSchemaValidator channelSchemaValidator;

    private DecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new DecisionTransformationService(channelSchemaValidator);
    }

    @Test
    void invalid_channel_schema() {
        // Arrange
        String invalidChannelPayload = "{\"channel\": \"UNKNOWN_TYPE\"}";
        when(channelSchemaValidator.validate(invalidChannelPayload))
                .thenThrow(new IllegalArgumentException("Invalid channel schema: unsupported type"));

        // Act & Assert
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> transformationService.transformDecision(invalidChannelPayload),
                "Should reject invalid channel schema during transformation"
        );

        assertEquals("Invalid channel schema: unsupported type", thrown.getMessage());
        verify(channelSchemaValidator, times(1)).validate(invalidChannelPayload);
        verifyNoMoreInteractions(channelSchemaValidator);
    }

    // Minimal domain/service classes to support the test scenario
    @FunctionalInterface
    interface ChannelSchemaValidator {
        void validate(String payload) throws IllegalArgumentException;
    }

    class DecisionTransformationService {
        private final ChannelSchemaValidator validator;

        DecisionTransformationService(ChannelSchemaValidator validator) {
            this.validator = validator;
        }

        void transformDecision(String payload) {
            // Input validation NFR compliance
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("Payload cannot be null or blank");
            }
            // Schema validation step in decision transformation
            validator.validate(payload);
            // Transformation logic continues...
        }
    }
}
