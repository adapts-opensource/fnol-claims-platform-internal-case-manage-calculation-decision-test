package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class IfMoratoriumActiveApplyBindingRestrictionRulesTest {

    @Mock
    private MoratoriumService moratoriumService;

    @Mock
    private BindingRestrictionService bindingRestrictionService;

    @InjectMocks
    private DecisionOrchestrationService decisionOrchestrationService;

    @Test
    void if_moratorium_active_apply_binding_restriction_rules() {
        // Arrange
        String insuredId = "INS-9876";
        when(moratoriumService.isActive(insuredId)).thenReturn(true);

        // Act
        EngagementDecision decision = decisionOrchestrationService.evaluateDecision(insuredId);

        // Assert
        assertNotNull(decision, "Decision result must not be null");
        assertTrue(decision.isBindingRestricted(), "Binding must be restricted when moratorium is active");
        verify(bindingRestrictionService, times(1)).applyRules(eq(insuredId), any());
        verify(moratoriumService, times(1)).isActive(eq(insuredId));
    }
}

// Supporting interfaces and DTOs for self-contained compilation
interface MoratoriumService {
    boolean isActive(String insuredId);
}

interface BindingRestrictionService {
    void applyRules(String insuredId, Object restrictionPayload);
}

class EngagementDecision {
    private boolean bindingRestricted;

    public boolean isBindingRestricted() {
        return bindingRestricted;
    }

    public void setBindingRestricted(boolean restricted) {
        this.bindingRestricted = restricted;
    }
}

class DecisionOrchestrationService {
    private MoratoriumService moratoriumService;
    private BindingRestrictionService bindingRestrictionService;

    public void setMoratoriumService(MoratoriumService service) {
        this.moratoriumService = service;
    }

    public void setBindingRestrictionService(BindingRestrictionService service) {
        this.bindingRestrictionService = service;
    }

    public EngagementDecision evaluateDecision(String insuredId) {
        EngagementDecision decision = new EngagementDecision();
        if (moratoriumService.isActive(insuredId)) {
            decision.setBindingRestricted(true);
            bindingRestrictionService.applyRules(insuredId, null);
        }
        return decision;
    }
}
