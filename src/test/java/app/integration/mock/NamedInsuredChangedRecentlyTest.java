package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Infra contract mocks for external I/O
interface AuditDiaryStoreService {
    String writeAuditLog(String entityId, String status, Map<String, Object> payload);
}

interface RulesEngineDecisionService {
    Map<String, Object> queryDecisionTable(String claimId, String ruleId);
}

interface WorkflowTaskRouterService {
    Map<String, String> getRoutingRules(String claimId);
}

// Service under test implementing Claim Data Standardization:decision:transformation
class ClaimDataStandardizationTransformationService {
    private final AuditDiaryStoreService auditDiaryStore;
    private final RulesEngineDecisionService rulesEngine;
    private final WorkflowTaskRouterService workflowTaskRouter;

    ClaimDataStandardizationTransformationService(AuditDiaryStoreService auditDiaryStore,
                                                  RulesEngineDecisionService rulesEngine,
                                                  WorkflowTaskRouterService workflowTaskRouter) {
        this.auditDiaryStore = auditDiaryStore;
        this.rulesEngine = rulesEngine;
        this.workflowTaskRouter = workflowTaskRouter;
    }

    Map<String, Object> transform(Map<String, Object> inputPayload) {
        String claimId = (String) inputPayload.get("id");
        String changeDateStr = (String) inputPayload.get("changeDate");
        
        // Input validation (NFR: security/input_validation)
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("claim id is required");
        }

        Map<String, Object> decision = rulesEngine.queryDecisionTable(claimId, "INS_CHANGED_RECENTLY");
        int thresholdDays = 30;
        if (decision != null && decision.containsKey("thresholdDays")) {
            thresholdDays = (Integer) decision.get("thresholdDays");
        }

        boolean changedRecently = false;
        if (changeDateStr != null) {
            LocalDate changeDate = LocalDate.parse(changeDateStr);
            changedRecently = ChronoUnit.DAYS.between(changeDate, LocalDate.now()) <= thresholdDays;
        }

        Map<String, Object> outputPayload = new HashMap<>(inputPayload);
        outputPayload.put("namedInsuredChangedRecently", changedRecently);
        outputPayload.put("status", "TRANSFORMED");

        if (changedRecently) {
            Map<String, String> routes = workflowTaskRouter.getRoutingRules(claimId);
            outputPayload.put("routingQueue", routes.getOrDefault("queue", "STANDARD"));
        }

        // Audit logging for observability and compliance (NFR: observability/compliance)
        String auditUri = auditDiaryStore.writeAuditLog(claimId, "TRANSFORMED", outputPayload);
        outputPayload.put("auditUri", auditUri);

        return outputPayload;
    }
}

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngine;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouter;

    @InjectMocks
    private ClaimDataStandardizationTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // Mock initialization handled by MockitoExtension
    }

    @Test
    void named_insured_changed_recently() {
        // Arrange
        String claimId = "CLM-98765";
        String historicalInsured = "Alice Johnson";
        String currentInsured = "Bob Smith";
        LocalDate changeDate = LocalDate.now().minusDays(5);
        LocalDate effectiveDate = LocalDate.now();

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("namedInsured", currentInsured);
        inputPayload.put("effectiveDate", effectiveDate.toString());
        inputPayload.put("changeDate", changeDate.toString());
        inputPayload.put("historicalInsured", historicalInsured);

        when(rulesEngine.queryDecisionTable(eq(claimId), eq("INS_CHANGED_RECENTLY")))
                .thenReturn(Map.of("ruleId", "INS_CHANGED_RECENTLY", "thresholdDays", 30));

        when(workflowTaskRouter.getRoutingRules(eq(claimId)))
                .thenReturn(Map.of("priority", "HIGH", "queue", "UNDERWRITING_REVIEW"));

        String expectedAuditUri = "s3://AuditDiaryStore-bucket/AuditDiaryStore/" + claimId + ".json";
        when(auditDiaryStore.writeAuditLog(eq(claimId), eq("TRANSFORMED"), anyMap()))
                .thenReturn(expectedAuditUri);

        // Act
        Map<String, Object> resultPayload = transformationService.transform(inputPayload);

        // Assert
        assertNotNull(resultPayload);
        assertEquals(claimId, resultPayload.get("id"));
        assertTrue((Boolean) resultPayload.get("namedInsuredChangedRecently"));
        assertEquals("TRANSFORMED", resultPayload.get("status"));
        assertEquals("UNDERWRITING_REVIEW", resultPayload.get("routingQueue"));
        assertEquals(expectedAuditUri, resultPayload.get("auditUri"));

        // Verify infra interactions
        verify(rulesEngine, times(1)).queryDecisionTable(eq(claimId), eq("INS_CHANGED_RECENTLY"));
        verify(workflowTaskRouter, times(1)).getRoutingRules(eq(claimId));
        verify(auditDiaryStore, times(1)).writeAuditLog(eq(claimId), eq("TRANSFORMED"), anyMap());
    }
}
