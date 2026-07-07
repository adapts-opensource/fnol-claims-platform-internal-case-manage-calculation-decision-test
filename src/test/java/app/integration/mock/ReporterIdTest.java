package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:transformation:orchestration.
 * Verifies handling of reporter_id during orchestration execution.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationTransformationOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient mockClaimDataStore;

    @Mock
    private TransformationEngine mockTransformationEngine;

    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Initialize service with mocked dependencies
        orchestrationService = new ClaimDataStandardizationOrchestrationService(
            mockTransformationEngine, 
            mockClaimDataStore
        );
    }

    @Test
    void reporter_id() {
        // Arrange
        String expectedReporterId = "RPT-NEWCO-001";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("reporter_id", expectedReporterId);
        inputPayload.put("claim_type", "AUTO");
        inputPayload.put("policy_number", "POL-998877");

        // Mock transformation output preserving reporter_id
        Map<String, Object> transformedPayload = new HashMap<>();
        transformedPayload.put("reporter_id", expectedReporterId);
        transformedPayload.put("standardized_status", "INITIATED");
        transformedPayload.put("claim_data_standardization_state_transition_orch", true);

        when(mockTransformationEngine.transform(anyMap())).thenReturn(transformedPayload);

        // Act
        Map<String, Object> result = orchestrationService.processOrchestration(inputPayload);

        // Assert
        assertNotNull(result, "Orchestration result should not be null");
        assertEquals(expectedReporterId, result.get("reporter_id"), 
            "reporter_id must be preserved and correctly standardized");

        // Verify infrastructure I/O contract mock
        verify(mockClaimDataStore).saveStateTransition(
            eq("Claim Data Store_table"),
            argThat(item -> {
                Object storedReporterId = item.get("reporter_id");
                return expectedReporterId.equals(storedReporterId);
            })
        );
        
        // Verify transformation was called with input payload
        verify(mockTransformationEngine).transform(inputPayload);
    }
}
