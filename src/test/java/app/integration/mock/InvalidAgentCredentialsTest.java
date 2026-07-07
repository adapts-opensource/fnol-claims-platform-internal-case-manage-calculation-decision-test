package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class InvalidAgentCredentialsTest {
    private ClaimOrchestrationService mockOrchestrationService;
    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        mockOrchestrationService = mock(ClaimOrchestrationService.class);
        orchestrator = new ClaimDataStandardizationOrchestrator(mockOrchestrationService);
    }

    @Test
    void invalid_agent_credentials() {
        String invalidCredential = "INVALID_AGENT_TOKEN";
        String claimId = "fnol-claim-001";

        when(mockOrchestrationService.validateAgentCredentials(invalidCredential))
            .thenThrow(new IllegalArgumentException("Invalid agent credentials"));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.processClaim(claimId, invalidCredential);
        });

        assertEquals("Invalid agent credentials", thrown.getMessage());
        verify(mockOrchestrationService, never()).saveStateToDynamoDB(any());
        verify(mockOrchestrationService, never()).uploadToS3(anyString(), any());
    }
}
