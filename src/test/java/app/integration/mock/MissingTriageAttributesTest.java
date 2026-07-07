package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissingTriageAttributesTest {

    @Mock
    private TriageAttributeProvider triageAttributeProvider;

    @Mock
    private ClaimContext claimContext;

    @InjectMocks
    private OrchestrationDecisionEngine orchestrationDecisionEngine;

    @Test
    void missing_triage_attributes() {
        // Arrange: Simulate missing triage attributes from external data persistence layer
        when(triageAttributeProvider.fetchAttributes(anyString())).thenReturn(null);
        when(claimContext.getClaimId()).thenReturn("CLM-INS-001");

        // Act & Assert: Orchestration must reject decision when triage attributes are absent
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrationDecisionEngine.evaluateDecision(claimContext);
        });

        // Verify triage provider was queried exactly once
        verify(triageAttributeProvider, times(1)).fetchAttributes("CLM-INS-001");
        // Ensure no downstream engagement or reserve services are triggered
        verifyNoMoreInteractions(triageAttributeProvider);
    }
}
