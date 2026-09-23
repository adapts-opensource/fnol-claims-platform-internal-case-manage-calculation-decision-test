package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private RulesEngineDecisionServiceClient rulesEngineClient;

    @Mock
    private AuditDiaryStoreClient auditDiaryStoreClient;

    @Test
    void rule_if_date_of_loss_is_within_30_days_of_expiration_policy_is_considered_active_n() {
        // Given
        LocalDate dateOfLoss = LocalDate.now();
        LocalDate policyExpiration = LocalDate.now().plusDays(25);
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateOfLoss", dateOfLoss.toString());
        payload.put("policyExpirationDate", policyExpiration.toString());
        payload.put("id", "claim-std-001");

        // Mock external I/O contracts (AWS/Production APIs)
        when(rulesEngineClient.queryDecisionRules("decision:transformation"))
                .thenReturn(Map.of("activeThresholdDays", 30));
        doNothing().when(auditDiaryStoreClient).writeToS3(anyString(), anyString());

        // When
        Map<String, Object> transformedPayload = transformClaimDecision(payload);

        // Then
        assertTrue((Boolean) transformedPayload.get("policyIsActive"),
                "Policy should be considered active when date_of_loss is within 30 days of expiration");
        assertEquals("ACTIVE", transformedPayload.get("policyStatus"),
                "Standardized policy status must be ACTIVE");
        verify(rulesEngineClient, times(1)).queryDecisionRules("decision:transformation");
        verify(auditDiaryStoreClient, times(1)).writeToS3(anyString(), anyString());
    }

    private Map<String, Object> transformClaimDecision(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>(payload);
        String lossDateStr = (String) result.get("dateOfLoss");
        String expDateStr = (String) result.get("policyExpirationDate");

        if (lossDateStr != null && expDateStr != null) {
            LocalDate lossDate = LocalDate.parse(lossDateStr);
            LocalDate expDate = LocalDate.parse(expDateStr);
            long daysBetween = ChronoUnit.DAYS.between(lossDate, expDate);

            Map<String, Object> ruleConfig = rulesEngineClient.queryDecisionRules("decision:transformation");
            int threshold = Integer.parseInt(String.valueOf(ruleConfig.getOrDefault("activeThresholdDays", 30)));

            if (daysBetween >= 0 && daysBetween <= threshold) {
                result.put("policyIsActive", true);
                result.put("policyStatus", "ACTIVE");
            }
        }
        // Mocked infra I/O: AuditDiaryStore_s3
        auditDiaryStoreClient.writeToS3("AuditDiaryStore-bucket", "AuditDiaryStore/claim-std-001.json");
        return result;
    }

    // Mock client interfaces to satisfy compilation without external dependencies
    private interface RulesEngineDecisionServiceClient {
        Map<String, Object> queryDecisionRules(String ruleKey);
    }

    private interface AuditDiaryStoreClient {
        void writeToS3(String bucketName, String objectKey);
    }
}
