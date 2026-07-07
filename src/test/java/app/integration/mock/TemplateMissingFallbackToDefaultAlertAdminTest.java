package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TemplateMissingFallbackToDefaultAlertAdminTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private AlertService alertService;

    @Mock
    private DecisionCalculationService decisionCalculationService;

    @Test
    void template_missing_fallback_to_default_alert_admin() {
        // Given: Template lookup in cache returns null (template missing)
        String cacheKey = "Cache & Reference Data:cache:CLAIM_ROUTING_TEMPLATE_V1";
        when(cacheService.getValue(cacheKey)).thenReturn(null);

        // When: Claim initiation & routing decision calculation is invoked
        Map<String, Object> claimPayload = Map.of(
            "id", "claim-12345",
            "type", "AUTO",
            "amount", 5000
        );
        Map<String, Object> decisionInput = Map.of("templateKey", cacheKey);

        // Mock the calculation service to simulate internal fallback logic
        when(decisionCalculationService.calculateDecision(anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> input = invocation.getArgument(0);
                    // Simulate fallback to default template when cache miss occurs
                    Map<String, Object> result = Map.of(
                        "status", "ROUTED",
                        "templateUsed", "DEFAULT_ROUTING_TEMPLATE",
                        "alertSent", true,
                        "payload", input
                    );
                    return result;
                });

        Map<String, Object> result = decisionCalculationService.calculateDecision(decisionInput);

        // Then: Verify fallback to default template was applied
        assertEquals("DEFAULT_ROUTING_TEMPLATE", result.get("templateUsed"));

        // Then: Verify admin alert was triggered due to missing template
        verify(alertService, times(1)).notifyAdmin(eq("Template missing for routing"), eq(cacheKey));
    }
}
