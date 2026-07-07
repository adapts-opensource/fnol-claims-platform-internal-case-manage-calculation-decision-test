package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionNoMatchRuleIntakeShellCreatedStatusTest {

    @Mock
    private ClaimDataStoreDynamoDb claimDataStore;

    @Mock
    private DocumentManagementS3 documentStore;

    @InjectMocks
    private StateTransitionOrchestrationService orchestrationService;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = Map.of(
            "id", "orch-12345-uuid",
            "status", "Unmatched FNOL",
            "intake_shell_created", true,
            "decision", "No match?",
            "expected_outcome", "Route to manual underwriting review"
        );
    }

    @Test
    void decision_no_match_rule_intake_shell_created_status_unmatched_fnol_expected_outcome_route_to_manual_underwriting_review() {
        // Given: Mock DynamoDB read for orchestration entity
        when(claimDataStore.getItemById(anyString())).thenReturn(testPayload);
        when(claimDataStore.putItem(anyString(), anyMap())).thenReturn(true);

        // Given: Mock S3 write for audit trail per infra contract
        when(documentStore.writeObject(anyString(), anyString(), anyMap())).thenReturn("s3://Document Management-bucket/orch-12345-uuid.json");

        // When: Execute state transition orchestration
        boolean transitionSuccess = orchestrationService.processTransition("orch-12345-uuid", testPayload);

        // Then: Verify payload routing outcome
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(claimDataStore).putItem(eq("orch-12345-uuid"), payloadCaptor.capture());

        Map<String, Object> updatedPayload = payloadCaptor.getValue();
        assertEquals("Route to manual underwriting review", updatedPayload.get("expected_outcome"));
        assertEquals("Unmatched FNOL", updatedPayload.get("status"));

        // Verify S3 audit log written with correct key pattern
        verify(documentStore).writeObject(
            eq("Document Management-bucket"),
            eq("orch-12345-uuid.json"),
            anyMap()
        );

        assertTrue(transitionSuccess);
    }
}
