package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionValidationTest {

    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        // Mock infrastructure I/O contracts to prevent live AWS/HTTP calls
        when(documentStoreService.storeObject(anyString(), anyString())).thenReturn("s3://mock-bucket/claim-123.json");
        when(policyValidationService.fetchPolicy(anyString())).thenReturn(Map.of("status", "ACTIVE"));
        when(rulesEngineService.evaluate(anyMap())).thenReturn(Map.of("riskScore", 45));
    }

    @Test
    void analyst_selects_incorrect_policy_without_justification() {
        // Arrange: Simulate claim data where analyst picks wrong policy and omits justification
        Map<String, Object> claimData = Map.of(
            "id", "claim-123",
            "payload", Map.of(
                "selectedPolicyId", "POL-INVALID",
                "justification", null
            )
        );

        // Act: Execute validation decision logic
        Map<String, Object> result = decisionService.validateDecision(claimData);

        // Assert: Verify decision fails due to missing justification for incorrect policy
        assertNotNull(result);
        assertFalse((Boolean) result.get("isValid"));
        assertEquals("JUSTIFICATION_MISSING", result.get("errorCode"));
        verify(rulesEngineService, times(1)).evaluate(anyMap());
        verify(policyValidationService, times(1)).fetchPolicy("POL-INVALID");
    }
}
