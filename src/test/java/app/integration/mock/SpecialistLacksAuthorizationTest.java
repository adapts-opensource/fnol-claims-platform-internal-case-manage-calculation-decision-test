package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SpecialistLacksAuthorizationTest {

    @Mock
    private AuditDiaryStoreS3 auditDiaryStoreS3;

    @Mock
    private RulesEngineDecisionDynamoDB rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouterDynamoDB workflowTaskRouter;

    private ClaimEnrichmentService claimEnrichmentService;

    @BeforeEach
    void setUp() {
        claimEnrichmentService = new ClaimEnrichmentService(auditDiaryStoreS3, rulesEngineDecisionService, workflowTaskRouter);
    }

    @Test
    void specialist_lacks_authorization() {
        // Given: Specialist ID that lacks authorization in the Rules Engine
        String claimId = "CLM-789";
        String specialistId = "SPEC-UNAUTH";
        String partitionKey = "pk";
        String sortKey = "sk";

        // Mock Rules Engine to return unauthorized status per feature contract
        Map<String, Object> denyPayload = Map.of(
                "authorization_status", "DENIED",
                "reason_code", "SPECIALIST_LACKS_AUTHORIZATION",
                "claim_id", claimId
        );
        when(rulesEngineDecisionService.getItem(partitionKey, sortKey)).thenReturn(denyPayload);

        // Mock S3 Audit Diary Store to verify it is NOT called for unauthorized claims (security/operability NFR)
        when(auditDiaryStoreS3.writeObject(anyString(), anyString(), any())).thenThrow(new IllegalStateException("Audit write should not occur for unauthorized enrichment requests"));

        // When: Enrichment is triggered for the claim
        EnrichmentOutcome outcome = claimEnrichmentService.processEnrichment(claimId, specialistId);

        // Then: Verify authorization check was performed, denied, and correct metadata is returned
        assertNotNull(outcome);
        assertEquals("DENIED", outcome.getAuthorizationStatus());
        assertTrue(outcome.isAuthorizationFailed());
        assertEquals("SPECIALIST_LACKS_AUTHORIZATION", outcome.getFailureReason());

        // Verify external I/O contracts
        verify(rulesEngineDecisionService, times(1)).getItem(partitionKey, sortKey);
        verifyNoInteractions(auditDiaryStoreS3);
        verify(workflowTaskRouter, never()).routeTask(anyString(), anyMap());
    }
}
