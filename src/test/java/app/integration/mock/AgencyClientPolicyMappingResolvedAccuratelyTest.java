package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

interface ClaimDataStandardizationOrchestrator {
    Map<String, Object> orchestrateTransformation(String claimId, Map<String, Object> payload);
}

class ClaimDataStandardizationOrchestrationTest {

    @Mock
    private ClaimDataStandardizationOrchestrator orchestrator;

    private Map<String, Object> inputPayload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        inputPayload = new HashMap<>();
        inputPayload.put("agencyId", "AG-90210");
        inputPayload.put("clientId", "CL-11235");
        inputPayload.put("policyId", "POL-55501");
        inputPayload.put("claimStatus", "INITIATED");
    }

    @Test
    void agency_client_policy_mapping_resolved_accurately() {
        // Arrange
        Map<String, Object> expectedStandardizedPayload = new HashMap<>();
        expectedStandardizedPayload.put("agencyId", "AG-90210");
        expectedStandardizedPayload.put("clientId", "CL-11235");
        expectedStandardizedPayload.put("policyId", "POL-55501");
        expectedStandardizedPayload.put("mappedAgencyName", "NewCo Preferred Agency");
        expectedStandardizedPayload.put("mappedPolicyStatus", "ACTIVE");
        expectedStandardizedPayload.put("standardizationVersion", "1.0");
        expectedStandardizedPayload.put("processingTimestamp", "2023-10-25T14:30:00Z");

        when(orchestrator.orchestrateTransformation("CLM-001", inputPayload))
                .thenReturn(expectedStandardizedPayload);

        // Act
        Map<String, Object> resultPayload = orchestrator.orchestrateTransformation("CLM-001", inputPayload);

        // Assert
        assertNotNull(resultPayload, "Result payload must not be null after orchestration");
        assertEquals("AG-90210", resultPayload.get("agencyId"), "Agency ID must be preserved");
        assertEquals("CL-11235", resultPayload.get("clientId"), "Client ID must be preserved");
        assertEquals("POL-55501", resultPayload.get("policyId"), "Policy ID must be preserved");
        assertEquals("NewCo Preferred Agency", resultPayload.get("mappedAgencyName"), "Agency name must be resolved");
        assertEquals("ACTIVE", resultPayload.get("mappedPolicyStatus"), "Policy status must be standardized");
        assertEquals("1.0", resultPayload.get("standardizationVersion"), "Version must match data model");
        assertTrue(resultPayload.containsKey("processingTimestamp"), "Timestamp must be added for observability");

        verify(orchestrator).orchestrateTransformation("CLM-001", inputPayload);
    }
}
