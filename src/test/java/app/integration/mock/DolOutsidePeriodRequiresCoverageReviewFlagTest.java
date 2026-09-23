package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DolOutsidePeriodRequiresCoverageReviewFlagTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimDataStandardizationValidationService claimValidationService;

    @BeforeEach
    void setUp() {
        claimValidationService = new ClaimDataStandardizationValidationService(
            documentStoreService, policyValidationService, rulesEngineService
        );
    }

    @Test
    void dol_outside_period_requires_coverage_review_flag() {
        // Arrange: Simulate DOL outside standard coverage window
        String claimId = "claim-789";
        LocalDate dateOfLoss = LocalDate.now().minusYears(3);
        LocalDate policyStart = LocalDate.now().minusMonths(6);
        LocalDate policyEnd = LocalDate.now().plusMonths(6);

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("dateOfLoss", dateOfLoss.toString());
        inputPayload.put("policyStartDate", policyStart.toString());
        inputPayload.put("policyEndDate", policyEnd.toString());

        // Mock S3 DocumentStoreService read
        when(documentStoreService.getObject(anyString(), anyString()))
            .thenReturn(inputPayload);

        // Mock DynamoDB PolicyValidationService read
        when(policyValidationService.getItem(anyString(), anyString()))
            .thenReturn(Map.of(
                "policyId", claimId,
                "status", "ACTIVE",
                "coverageStart", policyStart.toString(),
                "coverageEnd", policyEnd.toString()
            ));

        // Mock DynamoDB RulesEngineService read
        when(rulesEngineService.getItem(anyString(), anyString()))
            .thenReturn(Map.of("ruleId", "DOL_PERIOD_CHECK", "outcome", "OUTSIDE_PERIOD"));

        // Act
        Map<String, Object> standardizedPayload = claimValidationService.processClaimData(inputPayload);

        // Assert
        assertNotNull(standardizedPayload, "Standardized payload must not be null");
        assertEquals(claimId, standardizedPayload.get("id"), "Claim ID must be preserved");
        assertTrue((Boolean) standardizedPayload.get("coverage_review_flag"),
            "DOL outside period must trigger coverage review flag");
        assertFalse((Boolean) standardizedPayload.get("auto_approve_flag"),
            "DOL outside period must disable auto-approval");

        // Verify external I/O contracts were invoked exactly once
        verify(documentStoreService, times(1)).getObject(anyString(), anyString());
        verify(policyValidationService, times(1)).getItem(anyString(), anyString());
        verify(rulesEngineService, times(1)).getItem(anyString(), anyString());
        verifyNoMoreInteractions(documentStoreService, policyValidationService, rulesEngineService);
    }
}
