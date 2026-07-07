package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoricalClaimsFlagInfluencesRoutingPriorityTest {

    @Mock
    private DataStoreClient dataStoreClient;

    @Mock
    private ClaimIntakeClient claimIntakeClient;

    @Mock
    private CommunicationsHandlerClient communicationsHandlerClient;

    @InjectMocks
    private FnolSubmissionOrchestrator fnolSubmissionOrchestrator;

    private Map<String, Object> historicalClaimsState;
    private Map<String, Object> standardClaimsState;

    @BeforeEach
    void setUp() {
        historicalClaimsState = Map.of(
            "id", "state-trans-001",
            "payload", Map.of(
                "hasHistoricalClaims", true,
                "channel", "WEB_PORTAL",
                "estimatedLoss", 25000.0,
                "policyStatus", "ACTIVE"
            )
        );
        standardClaimsState = Map.of(
            "id", "state-trans-002",
            "payload", Map.of(
                "hasHistoricalClaims", false,
                "channel", "CALL_CENTER",
                "estimatedLoss", 1500.0,
                "policyStatus", "ACTIVE"
            )
        );
    }

    @Test
    void historical_claims_flag_influences_routing_priority() {
        // Arrange: Mock infrastructure I/O contracts (DynamoDB, S3, SES)
        when(dataStoreClient.putItem(anyString(), any(Map.class)))
            .thenReturn(Map.of("itemPayload", Map.of("validationStatus", "PASSED")));
        when(claimIntakeClient.storeSubmission(anyString(), anyString()))
            .thenReturn("s3://Claim Intake Service-bucket/state-trans-001.json");
        when(communicationsHandlerClient.sendNotification(anyString(), any(List.class), anyString()))
            .thenReturn("ses-msg-id-987");

        // Act: Validate and orchestrate submission with historical claims flag
        RoutingPriority actualPriority = fnolSubmissionOrchestrator.validateAndRoute(historicalClaimsState);

        // Assert: Historical claims flag must elevate routing priority
        assertEquals(RoutingPriority.HIGH, actualPriority,
            "Historical claims flag should influence routing priority to HIGH");
        
        // Verify infrastructure I/O contracts are invoked exactly once per submission
        verify(dataStoreClient, times(1)).putItem(anyString(), any(Map.class));
        verify(claimIntakeClient, times(1)).storeSubmission(anyString(), anyString());
        verify(communicationsHandlerClient, times(1)).sendNotification(anyString(), any(List.class), anyString());
        
        // Verify input validation and structured logging integration
        verify(dataStoreClient).putItem(eq("Data Store_table"), any(Map.class));
    }

    @Test
    void historical_claims_flag_absent_defaults_to_standard_priority() {
        // Arrange
        when(dataStoreClient.putItem(anyString(), any(Map.class)))
            .thenReturn(Map.of("itemPayload", Map.of("validationStatus", "PASSED")));
        when(claimIntakeClient.storeSubmission(anyString(), anyString()))
            .thenReturn("s3://Claim Intake Service-bucket/state-trans-002.json");
        when(communicationsHandlerClient.sendNotification(anyString(), any(List.class), anyString()))
            .thenReturn("ses-msg-id-654");

        // Act
        RoutingPriority actualPriority = fnolSubmissionOrchestrator.validateAndRoute(standardClaimsState);

        // Assert
        assertEquals(RoutingPriority.STANDARD, actualPriority,
            "Standard submissions without historical claims should default to STANDARD priority");
        
        verify(dataStoreClient, times(1)).putItem(anyString(), any(Map.class));
        verify(claimIntakeClient, times(1)).storeSubmission(anyString(), anyString());
        verify(communicationsHandlerClient, times(1)).sendNotification(anyString(), any(List.class), anyString());
    }
}
