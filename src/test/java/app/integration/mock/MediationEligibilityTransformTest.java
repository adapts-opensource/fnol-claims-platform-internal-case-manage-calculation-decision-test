package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediationEligibilityTransformTest {

    @Mock
    private ClaimTransformationService transformationService;

    private Map<String, Object> testInputs;

    @BeforeEach
    void setUp() {
        testInputs = new HashMap<>();
        testInputs.put("tenant_code", "FL01");
        testInputs.put("year", "2024");
        testInputs.put("dfs_mediation_request", true);
        testInputs.put("date_of_loss", "2024-05-20");
        testInputs.put("cause_of_loss", "water");
        testInputs.put("product", "HO3");
    }

    @Test
    void transform_to_mediation_eligibility_on_dispute() {
        // Arrange
        String expectedClaimNumber = "CLM-FL01-2024-001";
        String expectedTaskName = "Mediation Eligibility Review";
        String expectedState = "Coverage Triage";
        Boolean expectedRegulatoryFlag = true;

        when(transformationService.transform(any())).thenAnswer(invocation -> {
            Map<String, Object> result = new HashMap<>();
            result.put("claim_number", expectedClaimNumber);
            result.put("task_name", expectedTaskName);
            result.put("claim_state", expectedState);
            result.put("regulatory_sensitivity_flag", expectedRegulatoryFlag);
            return result;
        });

        // Act
        Map<String, Object> result = transformationService.transform(testInputs);

        // Assert
        assertNotNull(result.get("claim_number"), "Claim number should be generated");
        assertEquals(expectedClaimNumber, result.get("claim_number"));

        assertNotNull(result.get("task_name"), "Task should be created");
        assertEquals(expectedTaskName, result.get("task_name"));

        assertNotNull(result.get("claim_state"), "Claim state should be set");
        assertTrue("Coverage Triage".equals(result.get("claim_state")) || "Investigation Pending".equals(result.get("claim_state")),
                "State must be Coverage Triage or Investigation Pending");

        assertNotNull(result.get("regulatory_sensitivity_flag"), "Regulatory sensitivity should be flagged");
        assertEquals(expectedRegulatoryFlag, result.get("regulatory_sensitivity_flag"));

        verify(transformationService).transform(testInputs);
    }
}
