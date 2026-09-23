package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionIntegrationTest {

    @Mock
    private DynamoDbAdapter dynamoDbAdapter;

    @Mock
    private SesAdapter sesAdapter;

    @Mock
    private StateTransitionPolicy stateTransitionPolicy;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    private static final String CLAIM_ID = "claim-fnol-001";
    private static final String FNOL_INTAKE_ID = "intake-001";

    @BeforeEach
    void setUp() {
        // Infrastructure mocks initialized via MockitoExtension
    }

    @Test
    void applies_when_fnol_intake_is_submitted() {
        // Arrange
        Map<String, Object> claimPayload = Map.of(
            "pk", "CLAIM#" + CLAIM_ID,
            "sk", "METADATA",
            "status", "FNOL_INTAKE_SUBMITTED",
            "fnolIntakeId", FNOL_INTAKE_ID
        );

        when(dynamoDbAdapter.getItem(anyString(), any(Map.class))).thenReturn(Optional.of(claimPayload));
        when(stateTransitionPolicy.evaluate(any(Map.class))).thenReturn("UNDER_REVIEW");
        when(dynamoDbAdapter.updateItem(anyString(), any(Map.class), any(Map.class)))
            .thenReturn(Map.of("Attributes", Map.of("status", "UNDER_REVIEW")));
        when(sesAdapter.sendEmail(anyString(), anyList(), anyString()))
            .thenReturn("SES-MSG-ID-98765");

        // Act
        String transitionResult = insuredEngagementService.processStateTransition(CLAIM_ID, FNOL_INTAKE_ID);

        // Assert
        assertEquals("UNDER_REVIEW", transitionResult);
        verify(dynamoDbAdapter, times(1)).getItem(eq("ClaimsTable"), any(Map.class));
        verify(stateTransitionPolicy, times(1)).evaluate(claimPayload);
        verify(dynamoDbAdapter, times(1)).updateItem(eq("ClaimsTable"), any(Map.class), any(Map.class));
        verify(sesAdapter, times(1)).sendEmail(eq("claims@newco-insurance.com"), anyList(), eq("State Transition: Under Review"));
        verifyNoMoreInteractions(dynamoDbAdapter, sesAdapter, stateTransitionPolicy);
    }
}
