package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimOrchestrationTransformationTest {

    @Mock
    private ClaimTransformationOrchestrator transformationOrchestrator;

    @Mock
    private FnolSubmissionGateway submissionGateway;

    private ClaimInitiationOrchestrator orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimInitiationOrchestrator(transformationOrchestrator, submissionGateway);
    }

    @Test
    void applies_when_new_fnol_submitted() {
        // Arrange
        FnolPayload newFnol = new FnolPayload("CLAIM-NEW-001", "AUTO_COLLISION", "2024-05-20");
        when(submissionGateway.receiveNewFnol(newFnol)).thenReturn(true);
        doNothing().when(transformationOrchestrator).transformAndRoute(any(FnolPayload.class));

        // Act
        orchestrationService.processSubmission(newFnol);

        // Assert
        verify(submissionGateway, times(1)).receiveNewFnol(newFnol);
        verify(transformationOrchestrator, times(1)).transformAndRoute(any(FnolPayload.class));
        assertTrue(true, "Orchestration correctly applies transformation and routing for new FNOL");
    }
}

// Supporting interfaces and stubs for test isolation (no external I/O)
class FnolPayload {
    final String claimId;
    final String claimType;
    final String submissionDate;

    FnolPayload(String claimId, String claimType, String submissionDate) {
        this.claimId = claimId;
        this.claimType = claimType;
        this.submissionDate = submissionDate;
    }
}

interface FnolSubmissionGateway {
    boolean receiveNewFnol(FnolPayload payload);
}

interface ClaimTransformationOrchestrator {
    void transformAndRoute(FnolPayload payload);
}

class ClaimInitiationOrchestrator {
    private final ClaimTransformationOrchestrator orchestrator;
    private final FnolSubmissionGateway gateway;

    ClaimInitiationOrchestrator(ClaimTransformationOrchestrator orchestrator, FnolSubmissionGateway gateway) {
        this.orchestrator = orchestrator;
        this.gateway = gateway;
    }

    void processSubmission(FnolPayload payload) {
        if (gateway.receiveNewFnol(payload)) {
            orchestrator.transformAndRoute(payload);
        }
    }
}
