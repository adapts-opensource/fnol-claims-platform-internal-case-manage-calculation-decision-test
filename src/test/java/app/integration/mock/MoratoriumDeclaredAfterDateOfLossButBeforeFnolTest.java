package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:decision:transformation.
 * Validates infrastructure interactions and transformation logic for moratorium scenarios.
 */
public class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    private ClaimDataStandardizationDecisionTransformationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClaimDataStandardizationDecisionTransformationService(
                auditDiaryStore,
                rulesEngineDecisionService,
                workflowTaskRouter
        );
    }

    @Test
    void moratorium_declared_after_date_of_loss_but_before_fnol() {
        // Arrange: Scenario where moratorium_date > date_of_loss AND moratorium_date < fnol_date
        String claimId = "CLM-98765";
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 10);
        LocalDate moratoriumDate = LocalDate.of(2023, 10, 15);
        LocalDate fnolDate = LocalDate.of(2023, 10, 20);

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("date_of_loss", dateOfLoss.toString());
        inputPayload.put("moratorium_date", moratoriumDate.toString());
        inputPayload.put("fnol_date", fnolDate.toString());
        inputPayload.put("claim_type", "PROPERTY_DAMAGE");

        String expectedAuditUri = "s3://AuditDiaryStore-bucket/AuditDiaryStore/" + claimId + ".json";
        Map<String, Object> expectedDecision = Map.of("decision", "VALIDATED", "flag", "MORATORIUM_POST_LOSS");
        String expectedRoute = "MANUAL_REVIEW";

        // Mock S3 Audit Write
        when(auditDiaryStore.write(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json"), any(Map.class)))
                .thenReturn(expectedAuditUri);

        // Mock DynamoDB Decision Save
        when(rulesEngineDecisionService.saveDecision(eq("RulesEngineDecisionService_table"), any(Map.class)))
                .thenReturn(expectedDecision);

        // Mock DynamoDB Task Router
        when(workflowTaskRouter.route(eq("WorkflowTaskRouter_table"), anyString()))
                .thenReturn(expectedRoute);

        // Act
        Map<String, Object> result = service.transform(inputPayload);

        // Assert
        assertNotNull(result, "Transformation result must not be null");
        assertEquals("VALIDATED", result.get("decision"), "Decision should acknowledge loss occurred before moratorium");
        assertTrue(((String) result.get("flag")).contains("MORATORIUM_POST_LOSS"), "Payload should flag post-loss moratorium");

        // Verify Infra I/O Contracts
        verify(auditDiaryStore).write(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json"), any(Map.class));
        verify(rulesEngineDecisionService).saveDecision(eq("RulesEngineDecisionService_table"), any(Map.class));
        verify(workflowTaskRouter).route(eq("WorkflowTaskRouter_table"), anyString());
    }
}
