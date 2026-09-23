package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class PolicyDataChangesBetweenLoadAndSelectionTest {

    @Mock
    private PolicyDataStore policyDataStore;

    @Mock
    private DecisionEnrichmentService decisionEnrichmentService;

    @InjectMocks
    private ClaimDataStandardizationProcessor claimDataStandardizationProcessor;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection; shared setup can go here if needed
    }

    @Test
    void policy_data_changes_between_load_and_selection() {
        String claimId = "CLM-ENRICH-001";
        Map<String, Object> loadedPolicy = Map.of("policyId", "POL-882", "coverageLevel", "STANDARD", "riskScore", 72);
        Map<String, Object> selectedPolicy = Map.of("policyId", "POL-882", "coverageLevel", "PREMIUM", "riskScore", 88);

        when(policyDataStore.loadPolicy(claimId)).thenReturn(loadedPolicy);
        when(policyDataStore.selectPolicy(claimId)).thenReturn(selectedPolicy);

        Map<String, Object> enrichedResult = claimDataStandardizationProcessor.standardizeAndEnrich(claimId);

        assertNotNull(enrichedResult);
        assertEquals("PREMIUM", enrichedResult.get("coverageLevel"));
        assertEquals(88, enrichedResult.get("riskScore"));
        assertTrue((Boolean) enrichedResult.get("policyDataUpdated"));

        verify(policyDataStore).loadPolicy(claimId);
        verify(policyDataStore).selectPolicy(claimId);
        verify(decisionEnrichmentService).applyEnrichmentRules(selectedPolicy);
    }
}
