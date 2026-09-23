package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock tests for Claim Data Standardization: Decision: Enrichment.
 * Validates enrichment logic against mocked Rules Engine and Audit services.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Claim Data Standardization: Decision: Enrichment")
class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private RulesEngineDecisionServiceMock rulesEngineService;

    @Mock
    private AuditDiaryStoreMock auditStore;

    @InjectMocks
    private ClaimEnrichmentProcessor enrichmentProcessor;

    @BeforeEach
    void setUp() {
        // Mocks are automatically initialized by MockitoExtension.
        // Reset behavior between tests if shared state exists.
    }

    @Test
    @DisplayName("Moratorium active blocks coverage validation until lifted")
    void moratorium_active_blocks_coverage_validation_until_lifted() {
        // Arrange
        String claimId = "CLM-MOR-998877";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("moratoriumStatus", "ACTIVE");
        inputPayload.put("coverageType", "PROPERTY_DAMAGE");
        inputPayload.put("policyStatus", "VALID");
        inputPayload.put("moratoriumLiftDate", null);

        Map<String, Object> expectedDecision = new HashMap<>();
        expectedDecision.put("decisionCode", "BLOCK_COVERAGE");
        expectedDecision.put("validationResult", "BLOCKED");
        expectedDecision.put("reasonCode", "MORATORUM_ACTIVE");
        expectedDecision.put("message", "Coverage validation blocked: Moratorium active until lifted");
        expectedDecision.put("blocksCoverage", true);

        when(rulesEngineService.evaluateDecision(eq(claimId), anyMap()))
                .thenReturn(expectedDecision);

        // Act
        Map<String, Object> enrichedPayload = enrichmentProcessor.processEnrichment(inputPayload);

        // Assert
        assertNotNull(enrichedPayload, "Enriched payload should not be null");
        assertEquals("BLOCK_COVERAGE", enrichedPayload.get("decisionCode"), "Decision code should block coverage");
        assertEquals("BLOCKED", enrichedPayload.get("validationResult"), "Validation result should be blocked");
        assertTrue((Boolean) enrichedPayload.get("blocksCoverage"), "Coverage should be marked as blocked");
        
        String message = enrichedPayload.get("message").toString();
        assertTrue(message.contains("Moratorium active until lifted"), 
                "Message should indicate moratorium block reason");

        // Verify interactions
        verify(rulesEngineService).evaluateDecision(eq(claimId), anyMap());
        verify(auditStore).writeAuditLog(eq(claimId), any(Map.class));
    }
}
