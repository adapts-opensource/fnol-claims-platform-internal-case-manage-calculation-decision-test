package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private PolicyLookupRepository policyLookupRepository;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @InjectMocks
    private ClaimDataStandardizationEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        // Initialize shared test fixtures or reset state if required
    }

    @Test
    void appliesWhenUserSubmitsFnolWithPolicyNumberRiskAddressDateOfLossAndProductForm() {
        // Given: FNOL payload containing policy number, risk address, date of loss, and product form
        Map<String, Object> fnolPayload = Map.of(
            "policyNumber", "POL-2024-001",
            "riskAddress", "789 Elm Street, Metropolis, NY 10001",
            "dateOfLoss", "2024-05-20T14:30:00Z",
            "productForm", "AUTO_COMPREHENSIVE_COVERAGE"
        );

        // Mock external DynamoDB lookup for policy data
        when(policyLookupRepository.findByPolicyNumber("POL-2024-001"))
            .thenReturn(Map.of("policyStatus", "ACTIVE", "lineOfBusiness", "AUTO"));

        // Mock external Rules Engine decision evaluation
        when(rulesEngineDecisionService.evaluate(anyMap()))
            .thenReturn(Map.of(
                "decisionCode", "ENRICHMENT_APPLIED",
                "standardizedAddress", "789 ELM ST, METROPOLIS, NY, 10001",
                "riskTier", "STANDARD"
            ));

        // When: Enrichment decision is triggered
        Map<String, Object> enrichedPayload = enrichmentService.processEnrichment(fnolPayload);

        // Then: Verify enrichment applied correctly and external I/O was mocked
        assertNotNull(enrichedPayload);
        assertEquals("ENRICHMENT_APPLIED", enrichedPayload.get("decisionCode"));
        assertEquals("STANDARD", enrichedPayload.get("riskTier"));
        verify(policyLookupRepository).findByPolicyNumber("POL-2024-001");
        verify(rulesEngineDecisionService).evaluate(anyMap());
    }
}
