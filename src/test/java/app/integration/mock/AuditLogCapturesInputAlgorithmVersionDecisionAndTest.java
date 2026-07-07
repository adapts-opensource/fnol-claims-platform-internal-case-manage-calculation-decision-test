package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuditLogCapturesInputAlgorithmVersionDecisionAndOutputTest {

    private AuditLogCapture auditLogCapture;
    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditLogCapture = Mockito.mock(AuditLogCapture.class);
        orchestrator = new ClaimDataStandardizationOrchestrator(auditLogCapture);
    }

    @Test
    void auditLogCapturesInputAlgorithmVersionDecisionAndOutput() {
        // Given
        String inputPayload = "{\"claimId\":\"CLM-100\",\"type\":\"FNOL\"}";
        String algorithmVersion = "v2.1.0";
        String expectedDecision = "STANDARDIZED";
        String expectedOutput = "{\"claimId\":\"CLM-100\",\"type\":\"FNOL\",\"status\":\"PROCESSED\"}";

        // When
        orchestrator.process(inputPayload, algorithmVersion);

        // Then
        verify(auditLogCapture, times(1))
                .capture(inputPayload, algorithmVersion, expectedDecision, expectedOutput);
    }

    interface AuditLogCapture {
        void capture(String input, String algorithmVersion, String decision, String output);
    }

    static class ClaimDataStandardizationOrchestrator {
        private final AuditLogCapture auditLogCapture;

        ClaimDataStandardizationOrchestrator(AuditLogCapture auditLogCapture) {
            this.auditLogCapture = auditLogCapture;
        }

        void process(String input, String algorithmVersion) {
            String decision = "STANDARDIZED";
            String output = input.replace("}", ",\"status\":\"PROCESSED\"}");
            auditLogCapture.capture(input, algorithmVersion, decision, output);
        }
    }
}
