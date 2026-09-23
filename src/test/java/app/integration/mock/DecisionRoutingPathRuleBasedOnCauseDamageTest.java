package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
class DecisionRoutingPathRuleBasedOnCauseDamageTest {

    @Mock
    private ClaimTransformationOrchestration claimTransformationOrchestration;

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    private String claimId;
    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimId = "CLM-2023-STD-001";
        claimPayload = new HashMap<>();
        claimPayload.put("id", claimId);
        claimPayload.put("cause", "COLLISION");
        claimPayload.put("damage", "TOTAL_LOSS");
        claimPayload.put("matchStatus", "VERIFIED");
        claimPayload.put("dateOfLoss", LocalDate.of(2023, 10, 15));
        claimPayload.put("regulatoryFlags", Map.of("jurisdiction", "CA", "mandatoryReporting", true));
    }

    @Test
    void decisionRoutingPathRuleBasedOnCauseDamageMatchDateRegulatoryFlagsExpectedOutcomeHandlerAssignmentAndQueueSelection() {
        // Arrange
        Map<String, Object> expectedRoutingResult = new HashMap<>();
        expectedRoutingResult.put("handlerAssignment", "SPECIALIST_CA_TOTAL_LOSS");
        expectedRoutingResult.put("queueSelection", "HIGH_PRIORITY_TOTAL_LOSS_QUEUE");

        when(claimDataStoreClient.readItem(eq("Claim Data Store_table"), eq("pk"), eq(claimId)))
                .thenReturn(Map.of("id", claimId, "payload", claimPayload));

        when(claimTransformationOrchestration.processStandardization(eq(claimId), eq(claimPayload)))
                .thenReturn(expectedRoutingResult);

        // Act
        Map<String, Object> actualResult = claimTransformationOrchestration.processStandardization(claimId, claimPayload);

        // Assert
        assertNotNull(actualResult, "Routing result should not be null");
        assertEquals("SPECIALIST_CA_TOTAL_LOSS", actualResult.get("handlerAssignment"));
        assertEquals("HIGH_PRIORITY_TOTAL_LOSS_QUEUE", actualResult.get("queueSelection"));
        verify(claimDataStoreClient, times(1)).readItem(anyString(), anyString(), eq(claimId));
        verify(claimTransformationOrchestration, times(1)).processStandardization(eq(claimId), eq(claimPayload));
    }
}
