package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:transformation:orchestration.
 * Verifies routing logic adheres to agency hierarchy with override support.
 */
public class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private RulesTriageServiceClient rulesTriageClient;

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @InjectMocks
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void routing_follows_agency_hierarchy_unless_override_exists() {
        // Given: Payload with explicit override
        String claimId = "claim-override-001";
        Map<String, Object> payloadWithOverride = Map.of(
            "agencyId", "AGENCY_SPECIALTY",
            "hierarchyPath", "GLOBAL/US/EAST/AGENCY_SPECIALTY",
            "overrideRoutingId", "SPECIALTY_EXPERT_QUEUE"
        );

        when(rulesTriageClient.resolveRouting("AGENCY_SPECIALTY", "GLOBAL/US/EAST/AGENCY_SPECIALTY"))
            .thenReturn("SPECIALTY_EXPERT_QUEUE");

        // When: Orchestration processes the claim
        var result = orchestrationService.transformAndOrchestrate(claimId, payloadWithOverride);

        // Then: Override takes precedence
        assertEquals("SPECIALTY_EXPERT_QUEUE", result.getRoutingTarget());
        verify(rulesTriageClient, never()).applyDefaultHierarchyRules();

        // Given: Payload without override
        String claimIdNoOverride = "claim-hierarchy-002";
        Map<String, Object> payloadNoOverride = Map.of(
            "agencyId", "AGENCY_REGULAR",
            "hierarchyPath", "GLOBAL/US/WEST/AGENCY_REGULAR",
            "overrideRoutingId", null
        );

        when(rulesTriageClient.resolveRouting("AGENCY_REGULAR", "GLOBAL/US/WEST/AGENCY_REGULAR"))
            .thenReturn("WEST_REGION_STANDARD_QUEUE");

        // When: Orchestration processes the claim
        result = orchestrationService.transformAndOrchestrate(claimIdNoOverride, payloadNoOverride);

        // Then: Hierarchy path determines routing
        assertEquals("WEST_REGION_STANDARD_QUEUE", result.getRoutingTarget());
        verify(rulesTriageClient, times(1)).applyDefaultHierarchyRules();
    }
}
