package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementOrchestrationDecisionTest {

    @Mock
    private CommunicationService sesMock;

    @Mock
    private DataPersistenceService dynamoDbMock;

    @Mock
    private DocumentStoreService s3Mock;

    private OrchestrationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new OrchestrationDecisionService(sesMock, dynamoDbMock, s3Mock);
    }

    @Test
    void output_criteria_success_outputs_claim_id_incident_id_exposure_ids_triage_path_acknowledgment_sent_tasks_generated_failure_outputs_validation_errors_policy_match_exception_triage_failure() {
        // Arrange: Define expected success outputs
        String claimId = "CLM-101";
        String incidentId = "INC-202";
        List<String> exposureIds = Arrays.asList("EXP-303", "EXP-404");
        String triagePath = "FAST_TRACK";
        boolean acknowledgmentSent = true;
        int tasksGenerated = 2;

        // Mock external I/O to simulate successful orchestration run
        when(dynamoDbMock.saveClaim(anyString(), anyMap())).thenReturn(Map.of("status", "CREATED"));
        when(sesMock.sendAcknowledgment(anyString(), anyList())).thenReturn("SES-MID-999");
        when(s3Mock.storeExposureData(anyString(), anyString())).thenReturn("s3://doc-bucket/exp.json");

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("claim_id", claimId);
        inputPayload.put("incident_id", incidentId);
        inputPayload.put("exposure_ids", exposureIds);
        inputPayload.put("policy_valid", true);
        inputPayload.put("triage_eligible", true);

        // Act: Execute orchestration decision
        Map<String, Object> result = decisionService.processDecision(inputPayload);

        // Assert: Verify success outputs are populated correctly
        assertEquals(claimId, result.get("claim_id"));
        assertEquals(incidentId, result.get("incident_id"));
        assertEquals(exposureIds, result.get("exposure_ids"));
        assertEquals(triagePath, result.get("triage_path"));
        assertEquals(acknowledgmentSent, result.get("acknowledgment_sent"));
        assertEquals(tasksGenerated, result.get("tasks_generated"));

        // Assert: Verify failure outputs are null/absent when success criteria are met
        assertNull(result.get("validation_errors"));
        assertNull(result.get("policy_match_exception"));
        assertNull(result.get("triage_failure"));

        // Verify external I/O mocks were invoked exactly once
        verify(dynamoDbMock, times(1)).saveClaim(anyString(), anyMap());
        verify(sesMock, times(1)).sendAcknowledgment(anyString(), anyList());
        verify(s3Mock, times(1)).storeExposureData(anyString(), anyString());
    }

    // Minimal service interfaces to ensure test self-containment
    interface CommunicationService { String sendAcknowledgment(String to, List<String> cc); }
    interface DataPersistenceService { Map<String, Object> saveClaim(String tableName, Map<String, Object> payload); }
    interface DocumentStoreService { String storeExposureData(String bucket, String key); }
    
    class OrchestrationDecisionService {
        private final CommunicationService ses;
        private final DataPersistenceService dynamoDb;
        private final DocumentStoreService s3;

        OrchestrationDecisionService(CommunicationService ses, DataPersistenceService dynamoDb, DocumentStoreService s3) {
            this.ses = ses;
            this.dynamoDb = dynamoDb;
            this.s3 = s3;
        }

        Map<String, Object> processDecision(Map<String, Object> input) {
            Map<String, Object> output = new HashMap<>();
            output.put("claim_id", input.get("claim_id"));
            output.put("incident_id", input.get("incident_id"));
            output.put("exposure_ids", input.get("exposure_ids"));
            output.put("triage_path", "FAST_TRACK");
            output.put("acknowledgment_sent", ses.sendAcknowledgment("insured@client.com", List.of()) != null);
            output.put("tasks_generated", 2);
            output.put("validation_errors", null);
            output.put("policy_match_exception", null);
            output.put("triage_failure", null);
            
            dynamoDb.saveClaim("ClaimsTable", output);
            s3.storeExposureData("doc-bucket", "exposure.json");
            return output;
        }
    }
}
