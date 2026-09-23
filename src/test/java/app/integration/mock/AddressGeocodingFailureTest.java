package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

public class AddressGeocodingFailureTest {

    private GeocodingClient geocodingClient;
    private DynamoDbClient dynamoDbClient;
    private SesClient sesClient;
    private ComplianceLogger complianceLogger;
    private StateTransitionEngine stateTransitionEngine;

    @BeforeEach
    void setUp() {
        geocodingClient = mock(GeocodingClient.class);
        dynamoDbClient = mock(DynamoDbClient.class);
        sesClient = mock(SesClient.class);
        complianceLogger = mock(ComplianceLogger.class);
        stateTransitionEngine = new StateTransitionEngine(geocodingClient, dynamoDbClient, sesClient, complianceLogger);
    }

    @Test
    void address_geocoding_failure() {
        // Arrange
        String claimId = "CLM-789012";
        String fromState = ClaimState.PENDING_REVIEW.toString();
        String toState = ClaimState.GEOCODING_VALIDATED.toString();
        String insuredAddress = "123 Mock Blvd, Fakeville, ZZ 99999";

        when(geocodingClient.resolveCoordinates(insuredAddress))
                .thenThrow(new GeocodingServiceException("Geocoding API timeout: invalid address format"));

        // Act & Assert
        GeocodingServiceException thrown = assertThrows(
                GeocodingServiceException.class,
                () -> stateTransitionEngine.executeTransition(claimId, fromState, toState, insuredAddress)
        );

        assertEquals("Geocoding API timeout: invalid address format", thrown.getMessage());

        // Verify state remains unchanged (no DynamoDB write)
        verify(dynamoDbClient, never()).updateItem(anyString(), anyMap());

        // Verify compliance diary logs the failure with structured metadata
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(complianceLogger).logStructuredEvent(eq(claimId), eventCaptor.capture(), metadataCaptor.capture());
        assertTrue(eventCaptor.getValue().contains("STATE_TRANSITION_FAILED"));
        assertTrue(eventCaptor.getValue().contains("GEOCODING_FAILURE"));
        assertTrue(metadataCaptor.getValue().contains("address_validation"));

        // Verify failure notification email is triggered per security/compliance NFRs
        ArgumentCaptor<String[]> toCaptor = ArgumentCaptor.forClass(String[].class);
        verify(sesClient).sendNotification(eq("claims-alerts@newco-insurance.com"), toCaptor.capture(), anyString());
        assertArrayEquals(new String[]{"claims-team@newco-insurance.com"}, toCaptor.getValue());
    }

    // Minimal domain & infrastructure stubs for test isolation
    interface GeocodingClient {
        String resolveCoordinates(String address) throws GeocodingServiceException;
    }

    interface DynamoDbClient {
        void updateItem(String tableName, Map<String, Object> item);
    }

    interface SesClient {
        void sendNotification(String fromAddress, String[] toAddresses, String subject);
    }

    interface ComplianceLogger {
        void logStructuredEvent(String claimId, String eventType, String metadata);
    }

    static class GeocodingServiceException extends RuntimeException {
        GeocodingServiceException(String message) {
            super(message);
        }
    }

    static class StateTransitionEngine {
        private final GeocodingClient geocodingClient;
        private final DynamoDbClient dynamoDbClient;
        private final SesClient sesClient;
        private final ComplianceLogger complianceLogger;

        StateTransitionEngine(GeocodingClient geo, DynamoDbClient db, SesClient ses, ComplianceLogger logger) {
            this.geocodingClient = geo;
            this.dynamoDbClient = db;
            this.sesClient = ses;
            this.complianceLogger = logger;
        }

        void executeTransition(String claimId, String fromState, String toState, String address) throws GeocodingServiceException {
            // Step 1: Validate address via external geocoding service
            geocodingClient.resolveCoordinates(address);

            // Step 2: Persist state change (only reached on success)
            dynamoDbClient.updateItem("ClaimStatusTable", Map.of("pk", claimId, "current_state", toState));

            // Step 3: Log successful transition
            complianceLogger.logStructuredEvent(claimId, "STATE_TRANSITION_SUCCESS", "transitioned_to_" + toState);
        }
    }
}
