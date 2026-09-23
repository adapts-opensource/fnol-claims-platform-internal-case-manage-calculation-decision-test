package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

interface AuditDiaryStoreService {
    String writeAuditDiary(String bucketName, String objectKeyPattern, String payload);
}

interface RulesEngineDecisionService {
    Map<String, Object> queryDecision(String tableName, String partitionKey);
}

interface WorkflowTaskRouterService {
    String routeTask(String tableName, String partitionKey);
}

class ClaimDataStandardizationCalculationTransformService {
    private final AuditDiaryStoreService auditDiaryStoreService;
    private final RulesEngineDecisionService rulesEngineDecisionService;
    private final WorkflowTaskRouterService workflowTaskRouterService;

    ClaimDataStandardizationCalculationTransformService(AuditDiaryStoreService auditDiaryStoreService,
                                                        RulesEngineDecisionService rulesEngineDecisionService,
                                                        WorkflowTaskRouterService workflowTaskRouterService) {
        this.auditDiaryStoreService = auditDiaryStoreService;
        this.rulesEngineDecisionService = rulesEngineDecisionService;
        this.workflowTaskRouterService = workflowTaskRouterService;
    }

    Map<String, Object> transform(String id, Map<String, Object> payload) {
        String dateOfLossStr = (String) payload.get("dateOfLoss");
        LocalDate dateOfLoss = LocalDate.parse(dateOfLossStr);
        LocalDate effectiveDate = LocalDate.parse((String) payload.get("policyEffectiveDate"));
        LocalDate expirationDate = LocalDate.parse((String) payload.get("policyExpirationDate"));

        boolean isActive = !dateOfLoss.isBefore(effectiveDate) && !dateOfLoss.isAfter(expirationDate);
        long daysInCoverage = ChronoUnit.DAYS.between(effectiveDate, dateOfLoss);

        Map<String, Object> transformed = new HashMap<>();
        transformed.put("id", id);
        transformed.put("coveragePeriodStart", effectiveDate.toString());
        transformed.put("coveragePeriodEnd", expirationDate.toString());
        transformed.put("isCoverageActive", isActive);
        transformed.put("daysInCoverage", daysInCoverage);

        return transformed;
    }
}

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationCalculationTransformTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    private ClaimDataStandardizationCalculationTransformService transformService;

    @BeforeEach
    void setUp() {
        transformService = new ClaimDataStandardizationCalculationTransformService(
                auditDiaryStoreService,
                rulesEngineDecisionService,
                workflowTaskRouterService
        );
    }

    @Test
    void dol_validation_correctly_identifies_coverage_periods() {
        // Given
        String claimId = "CLM-12345";
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 15);
        LocalDate policyEffectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate policyExpirationDate = LocalDate.of(2024, 1, 1);

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("dateOfLoss", dateOfLoss.toString());
        inputPayload.put("policyEffectiveDate", policyEffectiveDate.toString());
        inputPayload.put("policyExpirationDate", policyExpirationDate.toString());
        inputPayload.put("claimType", "AUTO");

        Map<String, Object> expectedOutput = new HashMap<>();
        expectedOutput.put("id", claimId);
        expectedOutput.put("coveragePeriodStart", policyEffectiveDate.toString());
        expectedOutput.put("coveragePeriodEnd", policyExpirationDate.toString());
        expectedOutput.put("isCoverageActive", true);
        expectedOutput.put("daysInCoverage", ChronoUnit.DAYS.between(policyEffectiveDate, dateOfLoss));

        when(rulesEngineDecisionService.queryDecision(anyString(), anyString()))
                .thenReturn(expectedOutput);
        when(auditDiaryStoreService.writeAuditDiary(anyString(), anyString(), anyString()))
                .thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/CLM-12345.json");
        when(workflowTaskRouterService.routeTask(anyString(), anyString()))
                .thenReturn("ROUTED");

        // When
        Map<String, Object> result = transformService.transform(claimId, inputPayload);

        // Then
        assertNotNull(result);
        assertEquals(policyEffectiveDate.toString(), result.get("coveragePeriodStart"));
        assertEquals(policyExpirationDate.toString(), result.get("coveragePeriodEnd"));
        assertEquals(true, result.get("isCoverageActive"));
        assertEquals(288, result.get("daysInCoverage"));

        verify(rulesEngineDecisionService).queryDecision(eq("pk"), eq(claimId));
        verify(auditDiaryStoreService).writeAuditDiary(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json"), anyString());
        verify(workflowTaskRouterService).routeTask(eq("WorkflowTaskRouter_table"), eq(claimId));
    }
}
