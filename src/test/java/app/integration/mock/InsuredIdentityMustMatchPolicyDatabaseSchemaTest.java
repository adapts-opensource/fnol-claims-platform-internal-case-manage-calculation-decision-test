package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Claim Initiation & Routing:decision:calculation.
 * Verifies that insured identity validation against policy database schema works correctly via mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class InsuredIdentityMustMatchPolicyDatabaseSchemaTest {

    @Mock
    private PolicyDatabaseService policyDatabaseService;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private ClaimDecisionCalculator claimDecisionCalculator;

    @Test
    void insured_identity_must_match_policy_database_schema() {
        // Arrange: Valid payload with insured identity matching policy schema
        String claimId = "claim-init-001";
        Map<String, Object> payload = Map.of(
            "insured_identity", Map.of(
                "policy_key", "POL-9921",
                "entity_type", "INDIVIDUAL",
                "attributes", Map.of("date_of_birth", "1985-04-12", "national_id", "ID-8821")
            ),
            "claim_details", Map.of("type", "ACCIDENT")
        );

        // Mock policy DB schema validation success
        when(policyDatabaseService.validateInsuredIdentitySchema(any(Map.class))).thenReturn(true);
        when(cacheService.get(anyString(), anyInt())).thenReturn(null); // Cache miss scenario

        // Act: Process claim initiation decision
        ClaimInitiationResult result = claimDecisionCalculator.processClaimInitiation(claimId, payload);

        // Assert: Result must indicate schema validation passed
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSchemaValid(), "Insured identity must match policy database schema");
        assertEquals(claimId, result.getClaimId(), "Claim ID must be preserved");
        assertEquals(DecisionStatus.ROUTING_ELIGIBLE, result.getStatus(), "Decision should be routing eligible");

        // Verify: Ensure schema validation was invoked on the policy database service
        verify(policyDatabaseService).validateInsuredIdentitySchema(payload.get("insured_identity"));
        
        // Verify: Cache should not be updated for validation-only checks (idempotency)
        verify(cacheService, never()).put(anyString(), any());
    }
}
