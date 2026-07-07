package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CoverageFlagsUpdatedPerSelectedPolicyFormTest {

    @Mock
    private PolicyFormRuleEngine policyFormRuleEngine;

    @Mock
    private CoverageFlagEnrichmentService coverageFlagEnrichmentService;

    @InjectMocks
    private ClaimDataEnrichmentProcessor claimDataEnrichmentProcessor;

    private Map<String, Object> claimPayload;
    private String selectedPolicyForm;

    @BeforeEach
    void setUp() {
        claimPayload = new HashMap<>();
        claimPayload.put("claimId", "CLM-98765");
        claimPayload.put("policyForm", "AUTO_COMBO");
        claimPayload.put("coverageFlags", new HashMap<>());

        selectedPolicyForm = "AUTO_COMBO";
    }

    @Test
    void coverage_flags_updated_per_selected_policy_form() {
        Map<String, Object> expectedFlags = Map.of(
            "liability", true,
            "collision", true,
            "comprehensive", false
        );

        when(policyFormRuleEngine.resolve(selectedPolicyForm)).thenReturn(expectedFlags);
        when(coverageFlagEnrichmentService.apply(anyMap(), anyMap())).thenReturn(claimPayload);

        Map<String, Object> enrichedPayload = claimDataEnrichmentProcessor.enrich(claimPayload, selectedPolicyForm);

        assertNotNull(enrichedPayload);
        @SuppressWarnings("unchecked")
        Map<String, Object> actualFlags = (Map<String, Object>) enrichedPayload.get("coverageFlags");
        assertEquals(expectedFlags, actualFlags);
        verify(coverageFlagEnrichmentService).apply(claimPayload, expectedFlags);
    }
}
