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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDecisionTransformationTest {

    @InjectMocks
    private ClaimDecisionTransformationService claimDecisionTransformationService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = new HashMap<>();
        testPayload.put("claimId", "CLM-12345");
        testPayload.put("claimType", "AUTO");
        testPayload.put("status", "PENDING");
        testPayload.put("flags", new HashMap<>());
    }

    @Test
    void flags_are_correctly_applied() {
        // Arrange
        Map<String, Object> expectedFlags = new HashMap<>();
        expectedFlags.put("isStandardized", true);
        expectedFlags.put("decisionApplied", true);
        expectedFlags.put("complianceChecked", true);

        when(rulesEngineDecisionService.evaluateDecision(anyMap())).thenReturn(expectedFlags);

        // Act
        Map<String, Object> result = claimDecisionTransformationService.transform(testPayload);

        // Assert
        assertNotNull(result, "Transformation result must not be null");
        assertTrue(result.containsKey("flags"), "Result must contain the flags key");

        @SuppressWarnings("unchecked")
        Map<String, Object> actualFlags = (Map<String, Object>) result.get("flags");
        assertEquals(expectedFlags, actualFlags, "Applied flags must match expected decision flags");

        verify(rulesEngineDecisionService, times(1)).evaluateDecision(testPayload);
        verify(auditDiaryStore, times(1)).store(anyString(), anyString());
    }
}
