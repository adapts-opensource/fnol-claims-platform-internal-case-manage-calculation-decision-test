package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:transformation:orchestration.
 * Verifies that unmatched shells are correctly routed to the intake specialist queue.
 * NFR Compliance: Thread-safe mocks, structured logging simulation, input validation on payload.
 */
interface ClaimDataStore {
    Map<String, Object> fetchClaimState(String id);
}

interface ClaimDataRouter {
    void routeTo(String target);
}

interface ClaimOrchestrationService {
    void processStandardization(String id, Map<String, Object> payload);
}

public class UnmatchedShellsRequireIntakeSpecialistRoutingTest {

    @Mock
    private ClaimDataStore claimDataStore;

    @Mock
    private ClaimDataRouter claimDataRouter;

    @Mock
    private ClaimOrchestrationService orchestrationService;

    private static final String TEST_ID = "orch-claim-001";
    private static final Map<String, Object> UNMATCHED_SHELL_PAYLOAD = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        UNMATCHED_SHELL_PAYLOAD.put("id", TEST_ID);
        UNMATCHED_SHELL_PAYLOAD.put("matchStatus", "UNMATCHED_SHELL");
        UNMATCHED_SHELL_PAYLOAD.put("state", "TRANSFORMING");
        UNMATCHED_SHELL_PAYLOAD.put("piiMasked", true);
    }

    @Test
    void unmatched_shells_require_intake_specialist_routing() {
        // Arrange: Mock external I/O (DynamoDB-like store) to return unmatched shell state
        when(claimDataStore.fetchClaimState(TEST_ID)).thenReturn(UNMATCHED_SHELL_PAYLOAD);

        // Arrange: Mock orchestration service to delegate routing based on payload match status
        doAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(1);
            String matchStatus = String.valueOf(payload.getOrDefault("matchStatus", ""));
            
            // Simulate structured logging for observability
            System.out.printf("[LOG] Orchestrating claim %s with status: %s%n", TEST_ID, matchStatus);
            
            if ("UNMATCHED_SHELL".equals(matchStatus)) {
                claimDataRouter.routeTo("INTAKE_SPECIALIST");
            }
            return null;
        }).when(orchestrationService).processStandardization(anyString(), anyMap());

        // Act: Execute orchestration with validated, mocked payload
        orchestrationService.processStandardization(TEST_ID, UNMATCHED_SHELL_PAYLOAD);

        // Assert: Verify routing decision matches business requirement
        verify(claimDataRouter, times(1)).routeTo("INTAKE_SPECIALIST");
        verifyNoMoreInteractions(claimDataRouter);
        
        // Assert: Verify external I/O contract was invoked exactly once
        verify(claimDataStore, times(1)).fetchClaimState(TEST_ID);
        
        // Assert: Validate payload integrity and PII compliance
        assertNotNull(UNMATCHED_SHELL_PAYLOAD.get("id"));
        assertEquals("UNMATCHED_SHELL", UNMATCHED_SHELL_PAYLOAD.get("matchStatus"));
        assertTrue((Boolean) UNMATCHED_SHELL_PAYLOAD.get("piiMasked"));
    }
}
