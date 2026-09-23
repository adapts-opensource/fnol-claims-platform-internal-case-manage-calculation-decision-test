package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentMustBeMappedToPolicyToSubmitTest {

    @Mock
    private PolicyAgentMappingValidator policyAgentMappingValidator;

    @InjectMocks
    private ClaimOrchestrationService claimOrchestrationService;

    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        payload = new HashMap<>();
        payload.put("id", "claim-std-001");
        payload.put("agentId", "agent-123");
        payload.put("policyId", "policy-456");
        payload.put("state", "NEW");
    }

    @Test
    void agent_must_be_mapped_to_policy_to_submit() {
        when(policyAgentMappingValidator.isAgentMappedToPolicy("agent-123", "policy-456")).thenReturn(false);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            claimOrchestrationService.processClaim(payload);
        });

        assertEquals("Agent must be mapped to policy to submit", exception.getMessage());
        verify(policyAgentMappingValidator).isAgentMappedToPolicy("agent-123", "policy-456");
    }
}

interface PolicyAgentMappingValidator {
    boolean isAgentMappedToPolicy(String agentId, String policyId);
}

class ClaimOrchestrationService {
    private final PolicyAgentMappingValidator policyAgentMappingValidator;

    public ClaimOrchestrationService(PolicyAgentMappingValidator policyAgentMappingValidator) {
        this.policyAgentMappingValidator = policyAgentMappingValidator;
    }

    public void processClaim(Map<String, Object> payload) {
        String agentId = (String) payload.get("agentId");
        String policyId = (String) payload.get("policyId");

        if (!policyAgentMappingValidator.isAgentMappedToPolicy(agentId, policyId)) {
            throw new IllegalArgumentException("Agent must be mapped to policy to submit");
        }
        // Orchestration continues with standardization and state transition...
    }
}
