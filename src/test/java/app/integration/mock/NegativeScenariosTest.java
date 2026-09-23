package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NegativeScenarios {

    interface PolicyValidator {
        boolean isValid(String policyId);
    }

    interface TenantResolver {
        String resolveTenant(String tenantId);
    }

    @Mock
    private PolicyValidator policyValidator;

    @Mock
    private TenantResolver tenantResolver;

    @Mock
    private Logger auditLogger;

    @InjectMocks
    private FnolValidationDecisionService fnolValidationDecisionService;

    private Map<String, Object> negativePayload;

    @BeforeEach
    void setUp() {
        negativePayload = Map.of(
            "claim_number", "  ",
            "tenant_id", null,
            "policy_id", "POL-EXPIRED",
            "channel", "GUEST"
        );
    }

    @Test
    void negative_scenarios_59() {
        when(policyValidator.isValid("POL-EXPIRED")).thenReturn(false);
        when(tenantResolver.resolveTenant(null)).thenThrow(new IllegalArgumentException("Tenant ID cannot be null"));

        assertThrows(IllegalArgumentException.class, () ->
            fnolValidationDecisionService.decideAndValidate(negativePayload)
        );

        verify(policyValidator).isValid("POL-EXPIRED");
        verify(tenantResolver).resolveTenant(null);
        verify(auditLogger, never()).log(any(), any());
    }
}
