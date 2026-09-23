package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private ClaimValidationService claimValidationService;

    @InjectMocks
    private DecisionEngine decisionEngine;

    @Test
    void channel_source_lacks_required_fields() {
        // Arrange
        Map<String, Object> payload = Map.of(
            "id", "claim-123",
            "claim_type", "AUTO",
            "channel_source", Map.of("reference_id", "REF-001")
        );

        when(claimValidationService.validatePayload(payload))
            .thenReturn(ValidationResult.failure("CHANNEL_SOURCE_MISSING_REQUIRED_FIELDS"));

        // Act
        DecisionResult result = decisionEngine.process(payload);

        // Assert
        assertEquals(DecisionStatus.REJECTED, result.getStatus());
        assertTrue(result.getErrors().contains("CHANNEL_SOURCE_MISSING_REQUIRED_FIELDS"));
        verify(claimValidationService).validatePayload(payload);
    }
}
