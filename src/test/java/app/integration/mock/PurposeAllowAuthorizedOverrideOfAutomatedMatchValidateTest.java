package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionDecisionValidationTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private PolicyCoverageValidator policyCoverageValidator;
    @Mock
    private RoutingUpdateService routingUpdateService;
    @Mock
    private GuidewireClaimModel claimModelRepository;
    @Mock
    private DocumentMediaStore documentMediaStore;
    @Mock
    private CommunicationAckManager communicationAckManager;

    private FnolSubmissionDecisionValidationService serviceUnderTest;

    @BeforeEach
    void setUp() {
        serviceUnderTest = new FnolSubmissionDecisionValidationService(
                authorizationService,
                policyCoverageValidator,
                routingUpdateService,
                claimModelRepository,
                documentMediaStore,
                communicationAckManager
        );
    }

    @Test
    void purpose_allow_authorized_override_of_automated_match_validate_coverage_and_update_routing() {
        // Arrange
        String operatorId = "ops_user_42";
        String fnolId = "fnol-override-789";
        Map<String, Object> overrideRequest = Map.of(
                "id", fnolId,
                "payload", Map.of("matchOverride", true, "channel", "mobile", "reason", "authorized_manual_review")
        );
        Map<String, Object> validCoverage = Map.of("coverageStatus", "ACTIVE", "policyId", "POL-99");
        Map<String, Object> updatedRouting = Map.of("queue", "priority_handler", "handlerId", "handler-101");

        when(authorizationService.verifyOverridePermission(operatorId)).thenReturn(true);
        when(policyCoverageValidator.validateCoverageAndPersist(fnolId, overrideRequest)).thenReturn(validCoverage);
        when(routingUpdateService.assignAndPersist(fnolId, updatedRouting)).thenReturn(updatedRouting);

        // Act
        Map<String, Object> decisionResult = serviceUnderTest.processAuthorizedOverrideAndValidation(operatorId, fnolId, overrideRequest);

        // Assert
        assertNotNull(decisionResult);
        assertEquals("ACTIVE", decisionResult.get("coverageStatus"));
        assertEquals("priority_handler", decisionResult.get("queue"));
        assertEquals("handler-101", decisionResult.get("handlerId"));

        // Verify external I/O mocks
        verify(authorizationService).verifyOverridePermission(operatorId);
        verify(policyCoverageValidator).validateCoverageAndPersist(eq(fnolId), anyMap());
        verify(routingUpdateService).assignAndPersist(eq(fnolId), anyMap());
        verify(communicationAckManager, never()).sendAcknowledgement(anyString(), anyList(), anyString());
        verify(documentMediaStore, never()).storeDocument(anyString(), anyString(), anyMap());
    }
}
