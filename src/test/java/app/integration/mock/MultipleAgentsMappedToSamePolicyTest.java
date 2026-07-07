package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Data Standardization:transformation:orchestration.
 * Verifies orchestration behavior when multiple agents are mapped to the same policy.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataOrchestrationService orchestrationService;

    private String entityId;
    private Map<String, Object> inputPayload;

    @BeforeEach
    void setUp() {
        entityId = "orch-state-001";
        inputPayload = new HashMap<>();
        inputPayload.put("policyId", "POL-889900");
        inputPayload.put("claimId", "CLM-112233");
        inputPayload.put("agents", Arrays.asList(
                Map.of("agentId", "AGT-001", "role", "primary", "commission", 5.0),
                Map.of("agentId", "AGT-002", "role", "secondary", "commission", 3.0)
        ));
    }

    @Test
    void multipleAgentsMappedToSamePolicy() {
        // Arrange: Expected standardized payload after orchestration
        Map<String, Object> expectedStandardizedPayload = new HashMap<>();
        expectedStandardizedPayload.put("policyId", "POL-889900");
        expectedStandardizedPayload.put("claimId", "CLM-112233");
        expectedStandardizedPayload.put("agents", Arrays.asList(
                Map.of("agentId", "AGT-001", "role", "primary", "commission", 5.0, "status", "standardized"),
                Map.of("agentId", "AGT-002", "role", "secondary", "commission", 3.0, "status", "standardized")
        ));

        when(orchestrationService.processClaimData(any(), eq(inputPayload))).thenReturn(expectedStandardizedPayload);

        // Act: Invoke orchestration transformation
        Map<String, Object> resultPayload = orchestrationService.processClaimData(entityId, inputPayload);

        // Assert: Verify orchestration correctly handles multiple agents mapped to the same policy
        assertNotNull(resultPayload, "Standardized payload should not be null");
        assertEquals("POL-889900", resultPayload.get("policyId"), "Policy ID must remain consistent");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agents = (List<Map<String, Object>>) resultPayload.get("agents");
        assertEquals(2, agents.size(), "Should process exactly two agents");

        for (Map<String, Object> agent : agents) {
            assertEquals("POL-889900", agent.get("policyId"), "Each agent must be mapped to the same policy");
            assertEquals("standardized", agent.get("status"), "Agent status should be standardized");
        }

        verify(orchestrationService, times(1)).processClaimData(eq(entityId), eq(inputPayload));
    }
}
