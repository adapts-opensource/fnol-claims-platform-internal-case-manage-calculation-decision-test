package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class InternalCaseManagementDecisionCalculationTest {

    // Abstracted external I/O contracts for mocking
    interface ClaimMetadataService { Map<String, Object> pullMetadata(String claimId); }
    interface JurisdictionalRuleEngine { Map<String, Object> applyRules(Map<String, Object> metadata); }
    interface AcknowledgmentContentGenerator { String generate(Map<String, Object> metadata, Map<String, Object> rules); }
    interface ChannelDispatcher { boolean dispatch(String content, Set<String> channels); }
    interface AuditLogger { void log(Map<String, Object> context, String event, Map<String, Object> details); }
    interface InputValidator { boolean validate(Map<String, Object> input); }

    @Mock private ClaimMetadataService claimMetadataService;
    @Mock private JurisdictionalRuleEngine ruleEngine;
    @Mock private AcknowledgmentContentGenerator contentGenerator;
    @Mock private ChannelDispatcher channelDispatcher;
    @Mock private AuditLogger auditLogger;
    @Mock private InputValidator inputValidator;

    @InjectMocks private DecisionCalculationService decisionCalculationService;

    @Captor private ArgumentCaptor<Map<String, Object>> metadataCaptor;
    @Captor private ArgumentCaptor<Map<String, Object>> rulesCaptor;
    @Captor private ArgumentCaptor<String> contentCaptor;
    @Captor private ArgumentCaptor<Set<String>> channelsCaptor;
    @Captor private ArgumentCaptor<Map<String, Object>> auditLogCaptor;

    @BeforeEach
    void setUp() {
        // Reset mocks between tests to ensure thread-safety isolation
        clearMocks(claimMetadataService, ruleEngine, contentGenerator, channelDispatcher, auditLogger, inputValidator);
    }

    @Test
    void description_pulls_claim_metadata_applies_jurisdictional_rules_generates_acknowledgment_content_and_dispatches_via_configured_channels() {
        // Arrange
        String claimId = "CLM-2024-001";
        Map<String, Object> claimInput = Map.of("claimId", claimId, "jurisdiction", "CA", "amount", 5000);
        Map<String, Object> metadata = Map.of("id", claimId, "payload", Map.of("status", "OPEN", "insuredId", "INS-123"));
        Map<String, Object> jurisdictionalRules = Map.of("ruleSet", "CA_INS_2024", "requiresAcknowledgment", true, "channels", Set.of("EMAIL", "PORTAL"));
        String acknowledgmentContent = "Acknowledgment for claim " + claimId + " generated under CA rules.";
        Map<String, Object> decisionResult = Map.of("decisionId", "DEC-001", "status", "ACKNOWLEDGED", "timestamp", Instant.now());

        when(inputValidator.validate(claimInput)).thenReturn(true);
        when(claimMetadataService.pullMetadata(claimId)).thenReturn(metadata);
        when(ruleEngine.applyRules(metadata)).thenReturn(jurisdictionalRules);
        when(contentGenerator.generate(metadata, jurisdictionalRules)).thenReturn(acknowledgmentContent);
        when(channelDispatcher.dispatch(eq(acknowledgmentContent), anySet())).thenReturn(true);

        // Act
        Map<String, Object> result = decisionCalculationService.calculateDecision(claimInput);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertEquals("ACKNOWLEDGED", result.get("status"));

        // Verify sequence and interactions
        verify(inputValidator).validate(claimInput);
        verify(claimMetadataService).pullMetadata(claimId);
        verify(ruleEngine).applyRules(metadata);
        verify(contentGenerator).generate(metadata, jurisdictionalRules);
        verify(channelDispatcher).dispatch(eq(acknowledgmentContent), channelsCaptor.capture());
        assertTrue(channelsCaptor.getValue().containsAll(Set.of("EMAIL", "PORTAL")));
        verify(auditLogger).log(any(), eq("DECISION_CALCULATED"), auditLogCaptor.capture());
        assertTrue(auditLogCaptor.getValue().containsKey("claimId"));
        assertTrue(auditLogCaptor.getValue().containsKey("jurisdiction"));
        assertTrue(auditLogCaptor.getValue().containsKey("timestamp"));
    }

    // Stateless service implementation to be injected and tested
    static class DecisionCalculationService {
        private final InputValidator inputValidator;
        private final ClaimMetadataService claimMetadataService;
        private final JurisdictionalRuleEngine ruleEngine;
        private final AcknowledgmentContentGenerator contentGenerator;
        private final ChannelDispatcher channelDispatcher;
        private final AuditLogger auditLogger;

        DecisionCalculationService(InputValidator inputValidator, ClaimMetadataService claimMetadataService,
                                   JurisdictionalRuleEngine ruleEngine, AcknowledgmentContentGenerator contentGenerator,
                                   ChannelDispatcher channelDispatcher, AuditLogger auditLogger) {
            this.inputValidator = inputValidator;
            this.claimMetadataService = claimMetadataService;
            this.ruleEngine = ruleEngine;
            this.contentGenerator = contentGenerator;
            this.channelDispatcher = channelDispatcher;
            this.auditLogger = auditLogger;
        }

        public Map<String, Object> calculateDecision(Map<String, Object> input) {
            if (!inputValidator.validate(input)) {
                throw new IllegalArgumentException("Input validation failed");
            }
            String claimId = (String) input.get("claimId");
            Map<String, Object> metadata = claimMetadataService.pullMetadata(claimId);
            Map<String, Object> rules = ruleEngine.applyRules(metadata);
            String content = contentGenerator.generate(metadata, rules);
            @SuppressWarnings("unchecked")
            Set<String> channels = (Set<String>) rules.get("channels");
            channelDispatcher.dispatch(content, channels);
            auditLogger.log(Map.of("claimId", claimId, "jurisdiction", input.get("jurisdiction")), "DECISION_CALCULATED", Map.of("timestamp", Instant.now()));
            return Map.of("decisionId", "DEC-001", "status", "ACKNOWLEDGED", "timestamp", Instant.now());
        }
    }
}
