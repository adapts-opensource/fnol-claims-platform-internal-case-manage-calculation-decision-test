package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock integration test for Claim Data Standardization: validation: decision.
 * Verifies date range validation logic using mocked infrastructure.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationDecisionService claimDataStandardizationDecisionService;

    @Test
    void date_range_valid() {
        // Arrange: Construct payload with valid date range
        String entityId = "claim-data-std-val-001";
        Map<String, Object> payload = Map.of(
            "incidentDate", "2023-10-01",
            "reportDate", "2023-10-05",
            "policyStartDate", "2023-01-01",
            "policyEndDate", "2024-01-01"
        );

        // Mock RulesEngineService to return valid decision for date range
        when(rulesEngineService.evaluate(anyString(), anyMap()))
            .thenReturn(Map.of("status", "VALID", "decision", "ACCEPT"));

        // Act: Invoke validation decision
        Map<String, Object> result = claimDataStandardizationDecisionService.process(payload);

        // Assert: Verify result and interactions
        assertNotNull(result, "Result should not be null");
        assertEquals("VALID", result.get("status"));
        assertEquals("ACCEPT", result.get("decision"));

        // Verify infrastructure calls
        verify(rulesEngineService, times(1)).evaluate(anyString(), anyMap());
    }
}
