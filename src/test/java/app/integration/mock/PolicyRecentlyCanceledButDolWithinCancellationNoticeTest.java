package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClaimDataStandardizationDecisionMockTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    private ClaimDataStandardizationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        decisionService = new ClaimDataStandardizationDecisionService(
                policyValidationService, rulesEngineService, documentStoreService
        );
    }

    @Test
    void policy_recently_canceled_but_dol_within_cancellation_notice_period() {
        // Arrange
        String claimId = "CLM-67890";
        LocalDate cancellationDate = LocalDate.now().minusDays(3);
        LocalDate dateOfLoss = LocalDate.now().minusDays(1);
        int noticePeriodDays = 10;

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("policyCancellationDate", cancellationDate.toString());
        inputPayload.put("dateOfLoss", dateOfLoss.toString());
        inputPayload.put("cancellationNoticePeriodDays", noticePeriodDays);

        // Mock external I/O contracts (DynamoDB & S3)
        when(policyValidationService.validatePolicy(anyString())).thenReturn(Map.of("policyStatus", "CANCELED", "effectiveDate", "2023-01-01"));
        when(rulesEngineService.evaluateRules(anyMap())).thenReturn(Map.of("decisionCode", "INVALID_CANCELLATION_PERIOD"));
        when(documentStoreService.writeToBucket(anyString(), anyString())).thenReturn("s3://DocumentStoreService-bucket/CLM-67890.json");

        // Act
        Map<String, Object> result = decisionService.processStandardizationDecision(inputPayload);

        // Assert
        assertNotNull(result, "Result payload should not be null");
        assertEquals("INVALID_CANCELLATION_PERIOD", result.get("validationDecision"), "Decision should flag cancellation period violation");
        assertEquals("s3://DocumentStoreService-bucket/CLM-67890.json", result.get("objectUri"), "S3 write should be recorded");
        verify(policyValidationService, times(1)).validatePolicy(claimId);
        verify(rulesEngineService, times(1)).evaluateRules(anyMap());
        verify(documentStoreService, times(1)).writeToBucket(eq("DocumentStoreService-bucket"), eq("CLM-67890.json"));
    }

    // Minimal package-private interfaces for mock demonstration
    interface PolicyValidationService {
        Map<String, Object> validatePolicy(String policyId);
    }

    interface RulesEngineService {
        Map<String, Object> evaluateRules(Map<String, Object> payload);
    }

    interface DocumentStoreService {
        String writeToBucket(String bucketName, String objectKey);
    }

    // Service under test
    static class ClaimDataStandardizationDecisionService {
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;
        private final DocumentStoreService documentStoreService;

        ClaimDataStandardizationDecisionService(PolicyValidationService pvs, RulesEngineService res, DocumentStoreService dss) {
            this.policyValidationService = pvs;
            this.rulesEngineService = res;
            this.documentStoreService = dss;
        }

        Map<String, Object> processStandardizationDecision(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            policyValidationService.validatePolicy(claimId);
            Map<String, Object> ruleResult = rulesEngineService.evaluateRules(payload);
            String objectUri = documentStoreService.writeToBucket("DocumentStoreService-bucket", claimId + ".json");
            Map<String, Object> result = new HashMap<>();
            result.put("validationDecision", ruleResult.get("decisionCode"));
            result.put("objectUri", objectUri);
            return result;
        }
    }
}
