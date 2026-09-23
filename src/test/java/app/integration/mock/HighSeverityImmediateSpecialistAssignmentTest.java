package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class HighSeverityImmediateSpecialistAssignmentTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private ClaimsDataStore claimsDataStore;

    @InjectMocks
    private RoutingDecisionCalculator routingDecisionCalculator;

    @Test
    void high_severity_immediate_specialist_assignment() {
        // Given
        String claimId = "CLM-HS-2024-001";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "severity", "HIGH",
            "incidentType", "AUTO_COLLISION",
            "policyStatus", "ACTIVE"
        );

        RoutingDecision expectedDecision = RoutingDecision.builder()
            .assignmentType(AssignmentType.IMMEDIATE_SPECIALIST)
            .priority(Priority.CRITICAL)
            .build();

        when(cacheService.get(anyString())).thenReturn("ACTIVE");
        when(claimsDataStore.getItem(anyString(), anyString())).thenReturn(payload);

        // When
        RoutingDecision actualDecision = routingDecisionCalculator.calculate(payload);

        // Then
        assertNotNull(actualDecision);
        assertEquals(expectedDecision.getAssignmentType(), actualDecision.getAssignmentType());
        assertEquals(expectedDecision.getPriority(), actualDecision.getPriority());
        verify(cacheService).get("Cache & Reference Data:cache:policy:" + claimId);
        verifyNoMoreInteractions(cacheService, claimsDataStore);
    }
}
