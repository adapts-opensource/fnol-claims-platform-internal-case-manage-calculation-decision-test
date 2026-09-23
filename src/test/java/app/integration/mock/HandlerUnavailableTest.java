package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationHandlerUnavailableTest {

    @Mock
    private ClaimTransformationHandler mockHandler;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Explicit initialization for clarity; MockitoExtension handles injection in most runners
    }

    @Test
    void handler_unavailable() {
        // Arrange
        Map<String, Object> payload = Map.of("id", "claim-001", "type", "FNOL");
        when(mockHandler.transform(anyMap())).thenThrow(new IllegalStateException("Handler unavailable"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> orchestrator.orchestrate(payload));

        // Verify
        verify(mockHandler, times(1)).transform(payload);
    }
}
