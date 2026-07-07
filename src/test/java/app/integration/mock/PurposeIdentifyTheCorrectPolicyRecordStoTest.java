package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyIdentificationDecisionTest {

    @Mock
    private ClaimsPolicyDataStore policyDataStore;

    @Mock
    private CacheReferenceDataService cacheService;

    @InjectMocks
    private ClaimRoutingDecisionOrchestrator orchestrator;

    private Map<String, Object> validPayload;

    @BeforeEach
    void setUp() {
        validPayload = Map.of(
                "id", "FNOL-789",
                "payload", Map.of(
                        "policyNumber", "POL-456",
                        "riskDetails", Map.of("location", "US-NY", "vehicleType", "CAR")
                )
        );
    }

    @Test
    void purpose_identify_the_correct_policy_record_s_to_associate_with_the_fnol_based_on_provided_identifiers_and_risk_details() {
        // Arrange
        List<Map<String, Object>> expectedPolicies = List.of(Map.of("policyId", "POL-456", "status", "ACTIVE"));
        when(policyDataStore.queryByPolicyAndRisk("POL-456", Map.of("location", "US-NY", "vehicleType", "CAR")))
                .thenReturn(expectedPolicies);

        // Act
        List<Map<String, Object>> result = orchestrator.identifyPolicyRecords(validPayload);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("POL-456", result.get(0).get("policyId"));
        verify(policyDataStore, times(1)).queryByPolicyAndRisk("POL-456", Map.of("location", "US-NY", "vehicleType", "CAR"));
    }

    @Test
    void shouldReturnEmptyListWhenNoMatchingPolicyFound() {
        when(policyDataStore.queryByPolicyAndRisk(anyString(), anyMap())).thenReturn(List.of());

        List<Map<String, Object>> result = orchestrator.identifyPolicyRecords(validPayload);

        assertTrue(result.isEmpty());
        verify(cacheService, never()).getCacheEntry(anyString());
    }

    @Test
    void shouldHandleMultipleMatchingPolicies() {
        List<Map<String, Object>> expectedPolicies = List.of(
                Map.of("policyId", "POL-456", "status", "ACTIVE"),
                Map.of("policyId", "POL-457", "status", "ACTIVE")
        );
        when(policyDataStore.queryByPolicyAndRisk(anyString(), anyMap())).thenReturn(expectedPolicies);

        List<Map<String, Object>> result = orchestrator.identifyPolicyRecords(validPayload);

        assertEquals(2, result.size());
    }

    @Test
    void shouldValidateInputBeforePolicyLookup() {
        Map<String, Object> invalidPayload = Map.of("id", "FNOL-789", "payload", Map.of());

        assertThrows(IllegalArgumentException.class, () -> orchestrator.identifyPolicyRecords(invalidPayload));
    }
}
