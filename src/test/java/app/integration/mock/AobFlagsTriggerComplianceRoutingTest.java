package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AobFlagsTriggerComplianceRoutingTest {

    @Mock
    private FnolOrchestrationValidator fnolOrchestrationValidator;

    @Mock
    private ComplianceRoutingEngine complianceRoutingEngine;

    private String submissionId;
    private Map<String, Object> payloadWithAobFlag;

    @BeforeEach
    void setUp() {
        submissionId = UUID.randomUUID().toString();
        payloadWithAobFlag = Map.of(
            "submissionId", submissionId,
            "aobFlag", true,
            "channel", "CALL_CENTER",
            "claimType", "AUTO",
            "state", "SUBMITTED"
        );
    }

    @Test
    void aob_flags_trigger_compliance_routing() {
        // Arrange
        MultiChannelFnolSubmissionStateTransitionC stateTransition = new MultiChannelFnolSubmissionStateTransitionC(submissionId, payloadWithAobFlag);
        when(fnolOrchestrationValidator.validatePayload(stateTransition)).thenReturn(true);

        // Act
        boolean isComplianceTriggered = fnolOrchestrationValidator.processAndRoute(stateTransition);

        // Assert
        assertTrue(isComplianceTriggered, "AOB flags must trigger compliance routing");
        verify(complianceRoutingEngine).routeToComplianceQueue(eq(submissionId), any(Map.class));
        verifyNoMoreInteractions(complianceRoutingEngine);
    }
}
