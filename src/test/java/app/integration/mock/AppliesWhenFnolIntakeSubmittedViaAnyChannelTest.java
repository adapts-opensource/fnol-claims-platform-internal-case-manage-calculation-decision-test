package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementOrchestrationDecisionTest {

    @Mock
    private DynamoDbPersistence dataPersistence;

    @Mock
    private S3DocumentStore documentStore;

    @Mock
    private SesCommunication communicationService;

    @Mock
    private DecisionOrchestrator decisionOrchestrator;

    private InsuredEngagementOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new InsuredEngagementOrchestrationService(
            dataPersistence, documentStore, communicationService, decisionOrchestrator
        );
    }

    @Test
    void applies_when_fnol_intake_submitted_via_any_channel() {
        List<String> channels = List.of("WEB_PORTAL", "MOBILE_APP", "CALL_CENTER", "BROKER_PORTAL");

        for (String channel : channels) {
            String claimId = UUID.randomUUID().toString();
            Map<String, Object> intakePayload = Map.of(
                "claimId", claimId,
                "channel", channel,
                "policyNumber", "POL-12345",
                "incidentType", "COLLISION"
            );

            orchestrationService.processIntake(intakePayload);

            verify(decisionOrchestrator).triggerDecisionWorkflow(eq(claimId), any(Map.class));
            verify(dataPersistence).persistIntake(eq(claimId), any(Map.class));
            verifyNoInteractions(communicationService);
            verifyNoInteractions(documentStore);
        }
    }
}
