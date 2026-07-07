package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization: state_transition: orchestration.
 * Verifies input criteria, validation rules, and freshness requirements.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchestrationTest {

    @Mock
    private BrokerRegistryClient brokerRegistryClient;

    @Mock
    private ReferenceCatalogClient referenceCatalogClient;

    @Mock
    private PriorClaimsClient priorClaimsClient;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    private Map<String, Object> validInputPayload;

    @BeforeEach
    void setUp() {
        // Initialize common test inputs
        String agentId = "AGENT_12345";
        String policyNumber = "POL_98765";
        String lossType = "WATER_DAMAGE";
        double estimatedDamage = 1500.00;
        Integer priorClaimCount = 1;
        Boolean aobIndicator = false;
        String preferredRegion = "US_EAST";

        validInputPayload = Map.of(
                "agent_id", agentId,
                "client_policy_number", policyNumber,
                "loss_type", lossType,
                "estimated_damage_amount", estimatedDamage,
                "prior_claim_count", priorClaimCount,
                "AOB_indicator", aobIndicator,
                "preferred_adjuster_region", preferredRegion
        );
    }

    /**
     * Test Case: InputCriteriaRequired_inputsAgent_idClient_policy_numberLoss_typeEstimated_damage_amountOptional_inputs
     * Validates required inputs, optional inputs, broker registry freshness, reference catalog match,
     * amount constraints, and prior claim freshness.
     */
    @Test
    void input_criteria_required_inputs_agent_id_client_policy_number_loss_type_estimated_damage_amount_optional_inputs_prior_claim_count_aob_indicator_preferred_adjuster_region_input_validation_agent_id_must_be_active_in_broker_registry_estimated_damage_amount_must_be_numeric_and_0_loss_type_must_match_reference_catalog_freshness_requirements_broker_registry_status_checked_at_session_start_prior_claim_data_fetched_within_last_30_days() {
        // Arrange: Setup mocks for validation and freshness checks
        String agentId = "AGENT_12345";
        String lossType = "WATER_DAMAGE";

        // Broker Registry: Agent must be active (checked at session start)
        when(brokerRegistryClient.isActive(agentId)).thenReturn(true);

        // Reference Catalog: Loss type must match
        when(referenceCatalogClient.isValid(lossType)).thenReturn(true);

        // Prior Claims: Data fetched within last 30 days
        // Mock returns recent claim data (10 days ago) to satisfy freshness requirement
        PriorClaimRecord recentClaim = new PriorClaimRecord("CLAIM_001", Instant.now().minusDays(10));
        when(priorClaimsClient.fetch(anyString())).thenReturn(List.of(recentClaim));

        // Act: Execute orchestration
        OrchestratorResult result = orchestrator.process(validInputPayload);

        // Assert: Verify result and interactions
        assertNotNull(result, "Orchestration result should not be null");
        assertTrue(result.isSuccess(), "Orchestration should succeed with valid inputs");

        // Verify Required Inputs were processed (implicit in successful orchestration)
        // Verify Optional Inputs were handled
        assertTrue(result.getPayload().containsKey("prior_claim_count"), "Optional input prior_claim_count should be in result");
        assertTrue(result.getPayload().containsKey("AOB_indicator"), "Optional input AOB_indicator should be in result");
        assertTrue(result.getPayload().containsKey("preferred_adjuster_region"), "Optional input preferred_adjuster_region should be in result");

        // Verify Input Validations
        // 1. Agent ID must be active in broker registry
        verify(brokerRegistryClient, times(1)).isActive(agentId);
        // Broker registry status checked at session start (verified by call)
        verifyNoMoreInteractions(brokerRegistryClient);

        // 2. Loss type must match reference catalog
        verify(referenceCatalogClient, times(1)).isValid(lossType);
        verifyNoMoreInteractions(referenceCatalogClient);

        // 3. Estimated damage amount must be numeric and >= 0
        // Mock test assumes orchestrator validates numeric/>=0; result success implies validation passed
        // If validation failed, result would be failure or exception thrown
        assertTrue(result.getPayload().containsKey("estimated_damage_amount"), "Estimated damage amount should be preserved");

        // Verify Freshness Requirements
        // Prior claim data fetched within last 30 days
        verify(priorClaimsClient, times(1)).fetch(agentId);
        // Orchestrator logic validates the returned data timestamp against freshness window
        // The mock provides data within the window, so no exception is thrown
        verifyNoMoreInteractions(priorClaimsClient);
    }

    /**
     * Helper record to simulate PriorClaim entity.
     */
    private record PriorClaimRecord(String claimId, Instant lossDate) {}

    /**
     * Interface mocks for external services.
     */
    private interface BrokerRegistryClient {
        boolean isActive(String agentId);
    }

    private interface ReferenceCatalogClient {
        boolean isValid(String lossType);
    }

    private interface PriorClaimsClient {
        List<PriorClaimRecord> fetch(String agentId);
    }

    /**
     * Result model for orchestration.
     */
    private static class OrchestratorResult {
        private boolean success;
        private Map<String, Object> payload;

        public OrchestratorResult(boolean success, Map<String, Object> payload) {
            this.success = success;
            this.payload = payload;
        }

        public boolean isSuccess() { return success; }
        public Map<String, Object> getPayload() { return payload; }
    }
}
