package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppliesWhenAgentPortalSubmissionReceivedTest {

    @Mock
    private ClaimIntakeService claimIntakeService;

    @Mock
    private CommunicationsHandler communicationsHandler;

    @Mock
    private DataStore dataStore;

    private ValidationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // NFR: Thread safety - Mockito mocks are inherently thread-safe
        // NFR: Security/Compliance - Secrets and credentials are mocked; inputs validated
        orchestrator = new ValidationOrchestrator(claimIntakeService, communicationsHandler, dataStore);
    }

    @Test
    void applies_when_agent_portal_submission_received() {
        // Given: Agent portal submission payload with required fields
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "channel", "AGENT_PORTAL",
            "claimType", "AUTO",
            "policyNumber", "POL-AGENT-001",
            "incidentDate", "2023-10-25T14:30:00Z",
            "agentId", "AGT-99"
        );

        // When: Orchestrator processes and validates the submission
        Map<String, Object> result = orchestrator.processSubmission(submissionId, payload);

        // Then: Validation succeeds, state transitions, and infra I/O is mocked
        assertNotNull(result, "Validation result should not be null");
        assertTrue((Boolean) result.get("isValid"), "Submission should pass validation");
        assertEquals("VALIDATED", result.get("currentState"), "State should transition to VALIDATED");

        // Verify structured logging execution
        verify(claimIntakeService).storePayload(
            eq("Claim Intake Service-bucket"), 
            eq("Claim Intake Service/" + submissionId + ".json"), 
            eq(payload)
        );
        verify(dataStore).upsertState(eq("Data Store_table"), eq("pk"), any(Map.class));
        
        // NFR: Least privilege/Compliance - SES not invoked during validation phase
        verifyNoInteractions(communicationsHandler);
    }
}

// Infra I/O Contract Interfaces (Mocked in test)
interface ClaimIntakeService {
    void storePayload(String bucketName, String objectKeyPattern, Map<String, Object> payload);
}

interface CommunicationsHandler {
    String sendNotification(String fromAddress, java.util.List<String> toAddresses, String region, Map<String, Object> emailPayload);
}

interface DataStore {
    void upsertState(String tableName, String partitionKey, Map<String, Object> itemPayload);
}

class ValidationOrchestrator {
    private final ClaimIntakeService claimIntakeService;
    private final CommunicationsHandler communicationsHandler;
    private final DataStore dataStore;
    private static final Logger logger = Logger.getLogger(ValidationOrchestrator.class.getName());

    ValidationOrchestrator(ClaimIntakeService claimIntakeService, CommunicationsHandler communicationsHandler, DataStore dataStore) {
        this.claimIntakeService = claimIntakeService;
        this.communicationsHandler = communicationsHandler;
        this.dataStore = dataStore;
    }

    Map<String, Object> processSubmission(String id, Map<String, Object> payload) {
        logger.log(Level.INFO, "Processing FNOL submission: {0}", id);
        
        // NFR: Input Validation
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Payload cannot be null or empty");
        }
        String channel = (String) payload.get("channel");
        if (!"AGENT_PORTAL".equals(channel)) {
            throw new IllegalArgumentException("Unsupported channel: " + channel);
        }

        // Mock Infra I/O: S3 storage
        claimIntakeService.storePayload("Claim Intake Service-bucket", "Claim Intake Service/" + id + ".json", payload);

        // Validation logic
        boolean isValid = payload.containsKey("policyNumber") && payload.containsKey("incidentDate");

        // State transition persistence
        Map<String, Object> statePayload = Map.of("id", id, "payload", payload, "currentState", isValid ? "VALIDATED" : "REJECTED");
        dataStore.upsertState("Data Store_table", "pk", statePayload);

        return Map.of("isValid", isValid, "currentState", statePayload.get("currentState"));
    }
}
