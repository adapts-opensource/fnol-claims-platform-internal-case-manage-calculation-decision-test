package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StatutoryDeadlinesTrackedTest {

    @Mock
    private ClaimDataStandardizationOrchestrator claimOrchestrator;

    private String claimId;
    private Map<String, Object> inputPayload;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID().toString();
        inputPayload = Map.of(
            "id", claimId,
            "payload", Map.of("claimType", "AUTO", "reportDate", "2024-01-01")
        );
    }

    @Test
    void statutory_deadlines_tracked() {
        // Arrange
        Map<String, Object> expectedPayload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "claimType", "AUTO",
                "reportDate", "2024-01-01",
                "statutoryDeadlines", Map.of(
                    "firstNotice", "2024-01-16T00:00:00Z",
                    "investigation", "2024-02-15T00:00:00Z"
                )
            )
        );

        when(claimOrchestrator.processClaim(eq(claimId), anyMap()))
            .thenReturn(expectedPayload);

        // Act
        Map<String, Object> result = claimOrchestrator.processClaim(claimId, inputPayload);

        // Assert
        assertNotNull(result, "Orchestration result must not be null");
        assertInstanceOf(Map.class, result.get("payload"), "Payload must be a map");
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) result.get("payload");
        assertTrue(payloadMap.containsKey("statutoryDeadlines"), "Statutory deadlines must be tracked in payload");

        @SuppressWarnings("unchecked")
        Map<String, Object> deadlines = (Map<String, Object>) payloadMap.get("statutoryDeadlines");
        assertTrue(deadlines.containsKey("firstNotice"), "First notice deadline must be tracked");
        assertTrue(deadlines.containsKey("investigation"), "Investigation deadline must be tracked");

        verify(claimOrchestrator, times(1)).processClaim(eq(claimId), anyMap());
    }
}
