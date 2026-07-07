package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SinkholeSpecialtyTransformTest {

    @Mock
    private ClaimDataTransformationService transformationService;

    @Mock
    private TaskGenerationService taskGenerationService;

    @Mock
    private RoutingDispatchService routingDispatchService;

    @InjectMocks
    private ClaimTransformationEngine engine;

    @BeforeEach
    void setUp() {
        // Initialize test fixtures and mock configurations
    }

    @Test
    void transformToSinkholeClaimOnGroundMovement() {
        // Arrange
        String tenantCode = "FL01";
        int year = 2024;
        boolean sinkholeIndicator = true;
        String dateOfLoss = "2024-02-20";
        String causeOfLoss = "sinkhole";
        String product = "HO3";

        String expectedClaimNumber = "CLM-2024-FL01-001";
        String expectedClaimType = "Sinkhole/ground movement claim";
        String expectedTaskType = "Sinkhole Neutral Evaluation Review";
        String expectedQueue = "specialty_handling_queue";

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("tenant_code", tenantCode);
        inputPayload.put("year", year);
        inputPayload.put("sinkhole_indicator", sinkholeIndicator);
        inputPayload.put("date_of_loss", dateOfLoss);
        inputPayload.put("cause_of_loss", causeOfLoss);
        inputPayload.put("product", product);

        when(transformationService.transform(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            payload.put("claim_number", expectedClaimNumber);
            payload.put("claim_type", expectedClaimType);
            return payload;
        });

        doNothing().when(taskGenerationService).createTask(eq(expectedTaskType), anyString());
        doNothing().when(routingDispatchService).routeToQueue(eq(expectedQueue));

        // Act
        Map<String, Object> result = engine.processClaim(inputPayload);

        // Assert
        assertNotNull(result);
        assertEquals(expectedClaimNumber, result.get("claim_number"));
        assertEquals(expectedClaimType, result.get("claim_type"));

        verify(transformationService, times(1)).transform(anyMap());
        verify(taskGenerationService, times(1)).createTask(eq(expectedTaskType), anyString());
        verify(routingDispatchService, times(1)).routeToQueue(eq(expectedQueue));
    }
}
