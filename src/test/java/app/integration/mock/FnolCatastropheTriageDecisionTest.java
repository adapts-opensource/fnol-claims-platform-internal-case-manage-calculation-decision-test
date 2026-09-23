package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies FNOL catastrophe triage decision routing.
 * Thread-safe design: No static mutable state; isolated mocks per test.
 * Observability: Structured logging integration points are mocked to avoid console noise.
 * Compliance: PII validation and least-privilege IAM assumptions are enforced in the orchestrator layer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FnolCatastropheTriageDecision")
class FnolCatastropheTriageDecisionTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private RulesTriageServiceClient rulesTriageServiceClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    private FnolDecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new FnolDecisionOrchestrator(claimDataStoreClient, rulesTriageServiceClient, documentManagementClient);
    }

    @Test
    @DisplayName("fnol_catastrophe_triage_decision_routing")
    void fnolCatastropheTriageDecisionRouting() {
        // Arrange: FNOL inputs per contract
        Map<String, Object> fnolPayload = Map.of(
                "channel", "API",
                "policy_number", "POL-22222",
                "date_of_loss", "2024-08-15",
                "cause_of_loss", "Hurricane",
                "event_name", "Hurricane Milton",
                "catastrophe_code", "HUR-2024-08",
                "tenant_code", "FL01"
        );

        String expectedClaimType = "Catastrophe claim";
        String expectedState = "Claim Opened";
        List<String> expectedTasks = List.of("Review FNOL", "Acknowledge Claim", "Assign Adjuster", "Catastrophe Assignment");

        // Mock infrastructure I/O contracts (DynamoDB, S3, Rules Service)
        when(claimDataStoreClient.putItem(eq("Claim Data Store_table"), any(Map.class)))
                .thenReturn(Map.of("id", "claim-std-001", "payload", fnolPayload));
        when(rulesTriageServiceClient.query(eq("Rules & Triage Service_table"), any(Map.class)))
                .thenReturn(Map.of("claim_type", expectedClaimType, "state", expectedState));
        when(documentManagementClient.putObject(anyString(), anyString(), any()))
                .thenReturn("arn:aws:s3:::Document Management-bucket/claim-std-001.json");

        // Act: Execute decision orchestration
        Map<String, Object> result = orchestrator.evaluate(fnolPayload);

        // Assert: Verify decision outcomes match expected routing
        assertNotNull(result, "Decision result must not be null");
        assertEquals(expectedClaimType, result.get("claim_type"), "Claim type must be Catastrophe");
        assertEquals(expectedState, result.get("state"), "State must be Claim Opened");
        assertEquals(expectedTasks, result.get("tasks"), "Tasks must match triage assignment list");

        // Verify: Ensure infra contracts were invoked exactly once
        verify(claimDataStoreClient, times(1)).putItem(eq("Claim Data Store_table"), any(Map.class));
        verify(rulesTriageServiceClient, times(1)).query(eq("Rules & Triage Service_table"), any(Map.class));
        verify(documentManagementClient, times(1)).putObject(anyString(), anyString(), any());
    }
}
