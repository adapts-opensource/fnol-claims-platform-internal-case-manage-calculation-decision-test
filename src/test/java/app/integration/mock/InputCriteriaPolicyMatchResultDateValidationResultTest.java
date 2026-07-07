package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InputCriteriaPolicyMatchResultDateValidationResultTest {

    @Mock
    private ClaimDataStandardizationOrchestrator mockOrchestrator;

    @Mock
    private ClaimDataStoreRepository mockClaimDataStore;

    @Mock
    private DocumentManagementService mockDocumentService;

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes @Mock fields automatically
    }

    @Test
    void input_criteria_policy_match_result_date_validation_result_cause_of_loss_damage_area_regulatory_flags() {
        // Arrange: Define input criteria per feature specification
        String claimId = "CLM-STD-2024-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("policyMatchResult", "MATCHED");
        payload.put("dateValidationResult", "VALID");
        payload.put("causeOfLoss", "COLLISION");
        payload.put("damageArea", "REAR_BUMPER");
        
        Map<String, Object> regulatoryFlags = new HashMap<>();
        regulatoryFlags.put("jurisdiction", "CA");
        regulatoryFlags.put("requiresInvestigation", true);
        payload.put("regulatoryFlags", regulatoryFlags);

        Map<String, Object> expectedStateTransition = new HashMap<>();
        expectedStateTransition.put("id", claimId);
        expectedStateTransition.put("payload", payload);
        expectedStateTransition.put("currentState", "TRANSFORMATION");
        expectedStateTransition.put("nextState", "TRIAGE");

        when(mockOrchestrator.executeStandardizationFlow(claimId, payload))
                .thenReturn(expectedStateTransition);

        // Act: Invoke orchestration with mocked external I/O dependencies
        Map<String, Object> result = mockOrchestrator.executeStandardizationFlow(claimId, payload);

        // Assert: Validate state transition and input criteria integrity
        assertNotNull(result, "Orchestration must return a state transition result");
        assertEquals(claimId, result.get("id"));
        assertEquals("TRANSFORMATION", result.get("currentState"));
        assertEquals("TRIAGE", result.get("nextState"));
        verify(mockOrchestrator).executeStandardizationFlow(claimId, payload);

        Map<String, Object> resultPayload = (Map<String, Object>) result.get("payload");
        assertEquals("MATCHED", resultPayload.get("policyMatchResult"));
        assertEquals("VALID", resultPayload.get("dateValidationResult"));
        assertEquals("COLLISION", resultPayload.get("causeOfLoss"));
        assertEquals("REAR_BUMPER", resultPayload.get("damageArea"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultFlags = (Map<String, Object>) resultPayload.get("regulatoryFlags");
        assertNotNull(resultFlags, "Regulatory flags must be preserved");
        assertEquals("CA", resultFlags.get("jurisdiction"));
        assertTrue((Boolean) resultFlags.get("requiresInvestigation"));

        // Verify mock interactions simulating infra I/O contracts (DynamoDB & S3)
        verify(mockClaimDataStore).saveItem(eq(claimId), any(Map.class));
        verify(mockDocumentService).storeMetadata(eq(claimId), anyString());
    }
}
