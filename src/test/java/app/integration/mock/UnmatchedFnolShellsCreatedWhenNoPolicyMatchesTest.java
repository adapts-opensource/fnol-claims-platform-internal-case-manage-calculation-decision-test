package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UnmatchedFnolShellsCreatedWhenNoPolicyMatchesTest {

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private FnolShellPersistenceService fnolShellPersistenceService;

    private ClaimRoutingDecisionCalculator decisionCalculator;

    @BeforeEach
    void setUp() {
        decisionCalculator = new ClaimRoutingDecisionCalculator(policyLookupService, fnolShellPersistenceService);
    }

    @Test
    void unmatched_fnol_shells_created_when_no_policy_matches() {
        // Arrange
        String claimId = "claim-init-789";
        Map<String, Object> payload = Map.of(
                "policyNumber", "POL-NO-MATCH",
                "claimType", "AUTO_COLLISION",
                "incidentDate", "2023-11-15",
                "severity", "MINOR"
        );

        // Mock external policy lookup (DynamoDB/Redis) to return empty result
        when(policyLookupService.resolvePolicy(eq("POL-NO-MATCH"))).thenReturn(Collections.emptyList());

        // Act
        decisionCalculator.calculateRoutingDecision(claimId, payload);

        // Assert
        verify(policyLookupService, times(1)).resolvePolicy(eq("POL-NO-MATCH"));
        verify(fnolShellPersistenceService, times(1))
                .createUnmatchedFnolShell(eq(claimId), eq(payload));
        verifyNoMoreInteractions(policyLookupService);
    }
}
