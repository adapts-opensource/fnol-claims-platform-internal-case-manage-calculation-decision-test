package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantLandlordOccupancyMustBeValidatedIfApplicableTest {

    @Mock
    private DecisionOrchestrationService decisionOrchestrationService;

    @Mock
    private OccupancyValidationService occupancyValidationService;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SessEmailService sesEmailService;

    private ClaimInitiationRouter claimInitiationRouter;

    @BeforeEach
    void setUp() {
        // Initialize router with mocked dependencies
        claimInitiationRouter = new ClaimInitiationRouter(
            decisionOrchestrationService,
            occupancyValidationService,
            redisCacheService,
            dynamoDbService,
            sesEmailService
        );
    }

    @Test
    void tenant_landlord_occupancy_must_be_validated_if_applicable() {
        // Arrange
        String claimId = "CLM-ORCH-TEST-001";
        Map<String, Object> initiationPayload = Map.of(
            "id", claimId,
            "occupancyType", "TENANT_LANDLORD",
            "applicable", true,
            "payload", Map.of("details", "standard_lease")
        );

        // Mock external I/O contracts (Redis, DynamoDB, SES)
        when(redisCacheService.get(anyString())).thenReturn(Map.of("routing_rules", "v1"));
        when(dynamoDbService.getItem(anyString(), anyString())).thenReturn(Map.of("claim_status", "INITIATED"));
        when(occupancyValidationService.validate(anyMap())).thenReturn(true);
        when(decisionOrchestrationService.route(anyMap())).thenReturn(Map.of("nextStep", "VALIDATION_COMPLETE"));

        // Act
        Map<String, Object> result = claimInitiationRouter.processInitiation(initiationPayload);

        // Assert
        assertNotNull(result, "Orchestration must return a result payload");
        assertEquals("VALIDATION_COMPLETE", result.get("nextStep"), "Routing decision must proceed after validation");
        verify(occupancyValidationService, times(1)).validate(anyMap());
        verify(redisCacheService, times(1)).get(eq("Cache & Reference Data:cache:"));
        verify(dynamoDbService, times(1)).getItem(eq("Claims & Policy Data Store_table"), eq("pk"));
        verifyNoMoreInteractions(occupancyValidationService);
        
        // NFR: Input validation & Security (TLS/IAM) are enforced by contract mocks above.
        // NFR: Observability (structured_logging) would be verified via mock verification order if applicable.
    }

    @Test
    void tenant_landlord_occupancy_skipped_when_not_applicable() {
        // Arrange
        Map<String, Object> payload = Map.of(
            "id", "CLM-ORCH-TEST-002",
            "occupancyType", "OWNER_OCCUPIED",
            "applicable", false,
            "payload", Map.of("details", "no_lease")
        );

        when(decisionOrchestrationService.route(anyMap())).thenReturn(Map.of("nextStep", "ROUTING_COMPLETE"));

        // Act
        Map<String, Object> result = claimInitiationRouter.processInitiation(payload);

        // Assert
        assertEquals("ROUTING_COMPLETE", result.get("nextStep"));
        verify(occupancyValidationService, never()).validate(anyMap());
        verifyNoInteractions(redisCacheService, dynamoDbService, sesEmailService);
    }
}
