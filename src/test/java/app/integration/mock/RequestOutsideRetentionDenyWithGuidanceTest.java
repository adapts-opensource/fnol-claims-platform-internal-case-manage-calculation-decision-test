package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates decision transformation logic for insured engagement requests.
 * Ensures requests outside the retention window are denied with appropriate guidance,
 * while adhering to thread-safety (stateless service), structured logging, and least-privilege I/O patterns.
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementDecisionTransformationTest {

    @Mock
    private RetentionBoundaryService retentionBoundaryService;

    @Mock
    private GuidanceDeliveryService guidanceDeliveryService;

    @Mock
    private StructuredEventLogger eventLogger;

    private DecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // Initialize stateless service under test with mocked dependencies
        transformationService = new DecisionTransformationService(
                retentionBoundaryService,
                guidanceDeliveryService,
                eventLogger
        );
    }

    @Test
    void request_outside_retention_deny_with_guidance() {
        // Arrange
        String requestId = "REQ-OUTSIDE-RETENTION-001";
        String insuredId = "INS-889900";
        boolean isWithinRetention = false;
        when(retentionBoundaryService.isWithinRetentionPeriod(insuredId, requestId)).thenReturn(isWithinRetention);

        // Act
        DecisionResult result = transformationService.transform(requestId, insuredId);

        // Assert
        assertNotNull(result, "Decision result must not be null");
        assertEquals(DecisionStatus.DENIED, result.status(), "Request outside retention must be denied");
        assertNotNull(result.guidance(), "Guidance must be provided upon denial");
        assertTrue(result.guidance().toLowerCase().contains("outside retention"),
                "Guidance should explicitly mention retention boundary");
        assertTrue(result.guidance().toLowerCase().contains("guidance"),
                "Guidance should contain actionable instructions");

        // Verify external I/O and side effects
        verify(retentionBoundaryService, times(1)).isWithinRetentionPeriod(eq(insuredId), eq(requestId));
        verify(guidanceDeliveryService, times(1)).send(eq(insuredId), anyString());
        verify(eventLogger, times(1)).logStructured(eq("DECISION_TRANSFORM"), eq("RETENTION_EXCEEDED"), eq(requestId));
    }

    // Minimal internal types to ensure test compilation and isolation
    record DecisionResult(DecisionStatus status, String guidance) {}

    // Mock interfaces for external services (DynamoDB/SES abstracted)
    interface RetentionBoundaryService {
        boolean isWithinRetentionPeriod(String insuredId, String requestId);
    }

    interface GuidanceDeliveryService {
        void send(String insuredId, String guidanceMessage);
    }

    interface StructuredEventLogger {
        void logStructured(String category, String eventType, String correlationId);
    }
}
