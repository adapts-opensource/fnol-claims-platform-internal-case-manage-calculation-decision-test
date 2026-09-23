package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AddressMatchesMultiplePoliciesTest {

    @Mock
    private ClaimOrchestrationService claimOrchestrationService;

    @Mock
    private PolicyMatchingService policyMatchingService;

    private static final String CLAIM_ID = "claim-789";
    private static final String TEST_ADDRESS = "100 Innovation Dr, San Jose, CA 95134";
    private static final List<String> MATCHED_POLICY_IDS = List.of("POL-A", "POL-B");

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void address_matches_multiple_policies() {
        // Arrange
        when(policyMatchingService.findPoliciesForAddress(TEST_ADDRESS))
                .thenReturn(MATCHED_POLICY_IDS);

        when(claimOrchestrationService.transformAndRoute(eq(CLAIM_ID), anyMap()))
                .thenReturn(Map.of(
                        "state", "TRANSFORMING",
                        "routing", "MULTI_POLICY_TRIAGE",
                        "matchedPolicyCount", 2,
                        "policies", MATCHED_POLICY_IDS,
                        "nextState", "AWAITING_POLICY_SELECTION"
                ));

        // Act
        Map<String, Object> orchestrationResult = claimOrchestrationService.transformAndRoute(
                CLAIM_ID,
                Map.of("claimAddress", TEST_ADDRESS)
        );

        // Assert
        assertNotNull(orchestrationResult);
        assertEquals("TRANSFORMING", orchestrationResult.get("state"));
        assertEquals("MULTI_POLICY_TRIAGE", orchestrationResult.get("routing"));
        assertEquals(2, orchestrationResult.get("matchedPolicyCount"));
        assertSame(MATCHED_POLICY_IDS, orchestrationResult.get("policies"));
        assertEquals("AWAITING_POLICY_SELECTION", orchestrationResult.get("nextState"));

        verify(policyMatchingService).findPoliciesForAddress(TEST_ADDRESS);
        verify(claimOrchestrationService).transformAndRoute(eq(CLAIM_ID), anyMap());
    }
}
