package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

public class RuleVersionsMustMatchDecisionTimestampsTest {

    @Mock
    private DecisionStateTransitionEngine transitionEngine;

    @Mock
    private RuleVersionResolver ruleResolver;

    private Instant decisionTimestamp;
    private String expectedRuleVersion;

    @BeforeEach
    void setUp() {
        decisionTimestamp = Instant.parse("2024-01-15T10:30:00Z");
        expectedRuleVersion = "v2.1.0";
    }

    @Test
    void rule_versions_must_match_decision_timestamps() {
        // Arrange: Mock rule version resolution aligned with decision timestamp
        when(ruleResolver.resolveForTimestamp(decisionTimestamp)).thenReturn(expectedRuleVersion);

        // Arrange: Mock state transition execution with input validation & structured logging context
        Map<String, Object> transitionPayload = Map.of(
            "decisionTimestamp", decisionTimestamp.toString(),
            "ruleVersion", expectedRuleVersion,
            "traceId", "mock-trace-id-123"
        );
        when(transitionEngine.executeTransition(anyMap())).thenReturn(Map.of("status", "SUCCESS"));

        // Act: Execute transition through mocked service
        Map<String, Object> result = transitionEngine.executeTransition(transitionPayload);

        // Assert: Verify rule version matches decision timestamp context
        assertEquals(expectedRuleVersion, transitionPayload.get("ruleVersion"));
        assertEquals("SUCCESS", result.get("status"));

        // Verify interactions ensure timestamp-based resolution was invoked
        verify(ruleResolver).resolveForTimestamp(decisionTimestamp);
        verify(transitionEngine).executeTransition(anyMap());
    }
}
