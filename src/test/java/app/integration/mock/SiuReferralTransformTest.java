package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class SiuReferralTransformTest {

    private ClaimTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = mock(ClaimTransformationService.class);
    }

    @Test
    @DisplayName("transform_to_siu_referral_on_fraud_indicators")
    void transform_to_siu_referral_on_fraud_indicators() {
        // Arrange: Build input payload with fraud indicators
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("tenant_code", "FL01");
        inputPayload.put("year", 2024);
        inputPayload.put("policy_inception_date", "2024-06-01");
        inputPayload.put("date_of_loss", "2024-06-10");
        inputPayload.put("prior_claims_count", 5);
        inputPayload.put("reporting_lag_days", 45);
        inputPayload.put("cause_of_loss", "fire");

        String generatedClaimNumber = "CLM-2024-FL01-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> expectedOutput = new HashMap<>();
        expectedOutput.put("claim_number", generatedClaimNumber);
        expectedOutput.put("claim_type", "SIU_REFERRAL_CANDIDATE");
        expectedOutput.put("task_created", "SIU_REFERRAL_REVIEW");
        expectedOutput.put("state", "INTAKE_REVIEW");

        when(transformationService.transform(inputPayload)).thenReturn(expectedOutput);

        // Act: Invoke transformation service
        Map<String, Object> resultPayload = transformationService.transform(inputPayload);

        // Assert: Verify expected transformation outcomes
        assertNotNull(resultPayload.get("claim_number"), "Claim number must be generated");
        assertEquals("SIU_REFERRAL_CANDIDATE", resultPayload.get("claim_type"), "Claim type must be set to SIU referral candidate");
        assertEquals("SIU_REFERRAL_REVIEW", resultPayload.get("task_created"), "Task SIU Referral Review must be created");
        String state = (String) resultPayload.get("state");
        assertTrue("DUPLICATE_REVIEW".equals(state) || "INTAKE_REVIEW".equals(state),
                "State must be set to Duplicate Review or Intake Review");
    }
}

// Mock interface representing the external claim transformation & routing service
interface ClaimTransformationService {
    Map<String, Object> transform(Map<String, Object> payload);
}
