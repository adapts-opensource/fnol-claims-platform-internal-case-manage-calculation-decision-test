package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PolicyFormCalculationTest {

    @Mock
    private CacheReferenceDataService cacheService;
    @Mock
    private ClaimsPolicyDataStoreService dataStoreService;
    @Mock
    private CommunicationAcknowledgmentService communicationService;

    private PolicyFormDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new PolicyFormDecisionCalculator(cacheService, dataStoreService, communicationService);
    }

    @Test
    void policy_form() {
        String claimId = "claim-init-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("policyNumber", "PF-8842");
        payload.put("formType", "standard");

        String cacheKey = "Cache & Reference Data:cache:policy:" + payload.get("policyNumber");
        String cachedValue = "{\"version\":\"1.0\",\"valid\":true}";

        when(cacheService.get(eq(cacheKey))).thenReturn(cachedValue);
        when(dataStoreService.getItem(eq("Claims & Policy Data Store_table"), anyMap())).thenReturn(Map.of("pk", claimId, "payload", payload));

        Map<String, Object> result = calculator.calculate(claimId, payload);

        assertNotNull(result);
        assertEquals("VALID", result.get("validationStatus"));
        verify(cacheService).get(cacheKey);
        verify(dataStoreService).getItem(eq("Claims & Policy Data Store_table"), anyMap());
        verifyNoInteractions(communicationService);
    }
}
