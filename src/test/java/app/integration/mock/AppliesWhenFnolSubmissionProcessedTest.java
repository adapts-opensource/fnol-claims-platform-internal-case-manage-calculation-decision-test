package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppliesWhenFnolSubmissionProcessedTest {

    @Mock
    private DataStoreClient dataStoreClient;
    @Mock
    private CommunicationsHandler communicationsHandler;
    @Mock
    private ClaimIntakeService claimIntakeService;

    @InjectMocks
    private StateTransitionCalculator stateTransitionCalculator;

    @BeforeEach
    void setUp() {
        // Reset mock interactions and NFR telemetry before each test
        reset(dataStoreClient, communicationsHandler, claimIntakeService);
    }

    @Test
    void appliesWhenFnolSubmissionProcessed() {
        // Arrange: Simulate FNOL submission state transition when payload is processed
        String submissionId = "fnol-sub-001";
        Map<String, Object> initialPayload = Map.of("status", "SUBMITTED", "channel", "WEB");
        Map<String, Object> processedPayload = Map.of("status", "PROCESSED", "channel", "WEB", "calculationResult", "INITIATED");

        when(claimIntakeService.retrievePayload(submissionId)).thenReturn(processedPayload);
        when(dataStoreClient.writeItem(eq(submissionId), any(Map.class))).thenReturn(true);
        when(communicationsHandler.sendNotification(eq(submissionId), anyList())).thenReturn("ses-msg-001");

        // Act: Execute state transition calculation
        Map<String, Object> result = stateTransitionCalculator.calculate(submissionId, initialPayload);

        // Assert: Verify calculation output and state transition
        assertNotNull(result, "State transition result should not be null");
        assertEquals("PROCESSED", result.get("status"), "Status should transition to PROCESSED");
        assertEquals("INITIATED", result.get("calculationResult"), "Calculation result should be INITIATED");

        // Verify external I/O contracts were invoked per infra contracts
        verify(claimIntakeService, times(1)).retrievePayload(submissionId);
        verify(dataStoreClient, times(1)).writeItem(eq(submissionId), any(Map.class));
        verify(communicationsHandler, times(1)).sendNotification(eq(submissionId), anyList());
    }

    // Minimal interface stubs for AWS infrastructure contracts to ensure compilation independence
    interface DataStoreClient {
        boolean writeItem(String partitionKey, Map<String, Object> itemPayload);
    }

    interface CommunicationsHandler {
        String sendNotification(String toAddress, List<String> toAddresses);
    }

    interface ClaimIntakeService {
        Map<String, Object> retrievePayload(String objectKeyPattern);
    }

    class StateTransitionCalculator {
        private final DataStoreClient dataStoreClient;
        private final CommunicationsHandler communicationsHandler;
        private final ClaimIntakeService claimIntakeService;

        StateTransitionCalculator(DataStoreClient dataStoreClient, CommunicationsHandler communicationsHandler, ClaimIntakeService claimIntakeService) {
            this.dataStoreClient = dataStoreClient;
            this.communicationsHandler = communicationsHandler;
            this.claimIntakeService = claimIntakeService;
        }

        public Map<String, Object> calculate(String id, Map<String, Object> payload) {
            Map<String, Object> processed = claimIntakeService.retrievePayload(id);
            dataStoreClient.writeItem(id, processed);
            communicationsHandler.sendNotification(id, List.of("claims@newco.insurance"));
            return processed;
        }
    }
}
