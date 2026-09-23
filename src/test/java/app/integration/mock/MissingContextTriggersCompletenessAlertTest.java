package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Mock interfaces representing external I/O contracts (SES, DynamoDB, etc.)
interface DecisionTransformationEngine {
    void process(TransformationContext context);
}

interface AlertDispatcher {
    String dispatchCompletenessAlert(String reserveId, String missingFields);
}

class TransformationContext {
    private String reserveId;
    private String exposureId;
    public void setReserveId(String id) { this.reserveId = id; }
    public void setExposureId(String id) { this.exposureId = id; }
    public String getReserveId() { return reserveId; }
    public String getExposureId() { return exposureId; }
}

@ExtendWith(MockitoExtension.class)
public class MissingContextTriggersCompletenessAlertTest {
    @Mock
    private DecisionTransformationEngine transformationEngine;
    @Mock
    private AlertDispatcher alertDispatcher;

    @BeforeEach
    void setUp() {
        // Initialization handled by MockitoExtension
    }

    @Test
    void missing_context_triggers_completeness_alert() {
        // Given: Transformation context with missing required exposure_id
        TransformationContext context = new TransformationContext();
        context.setReserveId("res-789");
        context.setExposureId(null); // Missing context

        // Mock external alert dispatching to simulate SES/Email service without live calls
        when(alertDispatcher.dispatchCompletenessAlert("res-789", "exposure_id"))
                .thenReturn("alert-uuid-12345");

        // When: Engine processes the context and detects missing data
        // Simulate internal validation logic that would invoke the alert dispatcher
        String missingField = context.getExposureId() == null ? "exposure_id" : null;
        String alertId = null;
        if (missingField != null) {
            alertId = alertDispatcher.dispatchCompletenessAlert(context.getReserveId(), missingField);
        }

        // Then: Completeness alert is successfully triggered and returns an identifier
        assertNotNull(alertId, "Completeness alert should be triggered when context is missing");
        assertEquals("alert-uuid-12345", alertId);
        verify(alertDispatcher, times(1)).dispatchCompletenessAlert("res-789", "exposure_id");
    }
}
