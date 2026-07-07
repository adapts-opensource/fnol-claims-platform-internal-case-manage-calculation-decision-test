package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationCalculationTransformMockTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;
    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;
    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    @InjectMocks
    private ClaimDataStandardizationCalculationTransformService transformationService;

    @BeforeEach
    void setUp() {
        // Mock infra I/O contracts to prevent live AWS/HTTP calls
        doNothing().when(auditDiaryStoreService).writeAuditLog(anyString(), anyString());
        lenient().when(rulesEngineDecisionService.queryDecision(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(workflowTaskRouterService.routeTask(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void warnings_are_displayed_for_edge_cases() {
        // Edge Case 1: Null or empty payload
        var resultNull = transformationService.transform("claim-001", Map.of());
        assertTrue(resultNull.warnings().contains("Payload is null or empty"), "Should warn on empty payload");

        // Edge Case 2: Missing required calculation field
        var resultMissing = transformationService.transform("claim-002", Map.of("claimId", "claim-002"));
        assertTrue(resultMissing.warnings().contains("Missing required field: amount"), "Should warn on missing amount");

        // Edge Case 3: Invalid numeric format
        var resultInvalid = transformationService.transform("claim-003", Map.of("amount", "not_a_number"));
        assertTrue(resultInvalid.warnings().contains("Invalid numeric format for amount"), "Should warn on invalid format");

        // Edge Case 4: Negative amount (business rule constraint)
        var resultNegative = transformationService.transform("claim-004", Map.of("amount", -100.0));
        assertTrue(resultNegative.warnings().contains("Amount cannot be negative"), "Should warn on negative amount");

        // Control Case: Valid payload should produce no warnings
        var resultValid = transformationService.transform("claim-005", Map.of("amount", 1000.0));
        assertTrue(resultValid.warnings().isEmpty(), "Should have no warnings for valid payload");

        // Verify structured logging & infra I/O contracts were invoked
        verify(auditDiaryStoreService, times(5)).writeAuditLog(anyString(), anyString());
        verify(rulesEngineDecisionService, times(5)).queryDecision(anyString(), anyString());
        verify(workflowTaskRouterService, times(5)).routeTask(anyString(), anyString());
    }
}

// Supporting interfaces and classes for compilation
record TransformationResult(List<String> warnings) {}

interface AuditDiaryStoreService {
    void writeAuditLog(String bucketName, String objectKey);
}

interface RulesEngineDecisionService {
    Optional<Map<String, Object>> queryDecision(String tableName, String partitionKey);
}

interface WorkflowTaskRouterService {
    Optional<Map<String, Object>> routeTask(String tableName, String partitionKey);
}

class ClaimDataStandardizationCalculationTransformService {
    private final AuditDiaryStoreService auditDiaryStoreService;
    private final RulesEngineDecisionService rulesEngineDecisionService;
    private final WorkflowTaskRouterService workflowTaskRouterService;

    public ClaimDataStandardizationCalculationTransformService(AuditDiaryStoreService auditDiaryStoreService,
                                                               RulesEngineDecisionService rulesEngineDecisionService,
                                                               WorkflowTaskRouterService workflowTaskRouterService) {
        this.auditDiaryStoreService = auditDiaryStoreService;
        this.rulesEngineDecisionService = rulesEngineDecisionService;
        this.workflowTaskRouterService = workflowTaskRouterService;
    }

    public TransformationResult transform(String id, Map<String, Object> payload) {
        List<String> warnings = new ArrayList<>();
        
        // Infra I/O: Audit logging & rule/task routing
        auditDiaryStoreService.writeAuditLog("AuditDiaryStore-bucket", "AuditDiaryStore/" + id + ".json");
        rulesEngineDecisionService.queryDecision("RulesEngineDecisionService_table", "pk");
        workflowTaskRouterService.routeTask("WorkflowTaskRouter_table", "pk");

        if (payload == null || payload.isEmpty()) {
            warnings.add("Payload is null or empty");
            return new TransformationResult(warnings);
        }

        if (!payload.containsKey("amount")) {
            warnings.add("Missing required field: amount");
        } else {
            Object amountObj = payload.get("amount");
            if (!(amountObj instanceof Number)) {
                warnings.add("Invalid numeric format for amount");
            } else {
                double amount = ((Number) amountObj).doubleValue();
                if (amount < 0) {
                    warnings.add("Amount cannot be negative");
                }
            }
        }
        return new TransformationResult(warnings);
    }
}
