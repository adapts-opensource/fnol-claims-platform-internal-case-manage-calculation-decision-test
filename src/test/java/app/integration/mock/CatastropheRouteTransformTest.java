package app.integration.mock;

import app.integration.mock.model.ClaimInputs;
import app.integration.mock.model.ClaimResult;
import app.integration.mock.service.ClaimInitiationService;
import app.integration.mock.service.RoutingCalculator;
import app.integration.mock.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatastropheRouteTransformTest {

    @Mock
    private ClaimInitiationService claimInitiationService;

    @Mock
    private RoutingCalculator routingCalculator;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private ClaimTransformationEngine claimTransformationEngine;

    @BeforeEach
    void setUp() {
        // Initialize mocks via MockitoExtension
    }

    @Test
    void route_to_catastrophe_on_named_storm() {
        // Arrange
        ClaimInputs inputs = new ClaimInputs();
        inputs.setTenantCode("FL01");
        inputs.setYear(2024);
        inputs.setEventName("HurricaneIdalia");
        inputs.setDateOfLoss("2024-09-10");
        inputs.setCauseOfLoss("hurricane");
        inputs.setProduct("HO3");
        inputs.setSeverity("high");

        String expectedClaimNumber = "FL01-2024-CLM-998877";
        when(claimInitiationService.initiate(any())).thenReturn(expectedClaimNumber);
        when(routingCalculator.calculate(any())).thenReturn(ClaimType.CATASTROPHE);

        // Act
        ClaimResult result = claimTransformationEngine.transformAndRoute(inputs);

        // Assert
        assertNotNull(result.getClaimNumber(), "Claim number must be generated");
        assertEquals(expectedClaimNumber, result.getClaimNumber());
        assertEquals(ClaimType.CATASTROPHE, result.getClaimType(), "Claim type must be set to Catastrophe");

        // Verify Side Effects
        verify(taskService).createTask("Catastrophe Assignment", result.getClaimNumber());
        verify(routingCalculator).routeTo("Catastrophe Inspection Queue", result.getClaimNumber());
    }
}
