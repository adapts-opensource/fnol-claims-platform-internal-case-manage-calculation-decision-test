package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WaterMinorSelfServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private SesClient sesClient;

    @InjectMocks
    private OrchestrationDecisionService orchestrationDecisionService;

    private Incident mockIncident;

    @BeforeEach
    void setUp() {
        mockIncident = mock(Incident.class);
        when(mockIncident.getDamageType()).thenReturn(DamageType.WATER);
        when(mockIncident.getEstimatedLoss()).thenReturn(500.00);
        when(mockIncident.getCurrency()).thenReturn("USD");
        when(mockIncident.isMinor()).thenReturn(true);
    }

    @Test
    void water_minor_self_service() {
        // Given: Water damage + minor severity/amount
        // When: Orchestration decision is evaluated
        RoutingDecision decision = orchestrationDecisionService.decide(mockIncident);

        // Then: Should route to self_service
        assertEquals(RoutingDecision.SELF_SERVICE, decision);
        verify(orchestrationDecisionService, times(1)).decide(mockIncident);
        // Verify no live AWS calls are made during decision evaluation
        verifyNoInteractions(dynamoDbClient, s3Client, sesClient);
    }
}
