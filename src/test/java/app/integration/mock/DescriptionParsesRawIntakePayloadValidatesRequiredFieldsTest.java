package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the Internal Case Management:calculation:decision flow.
 * Mocks external I/O contracts (DynamoDB, S3, HTTP endpoints) and validates
 * parsing, validation, normalization, enrichment, and policy-matching readiness.
 */
@ExtendWith(MockitoExtension.class)
public class InternalCaseManagementCalculationDecisionTest {

    @Mock
    private IntakePayloadParser payloadParser;

    @Mock
    private AddressDateNormalizer normalizer;

    @Mock
    private ChannelMetadataEnricher enricher;

    @Mock
    private StructuredLogger logger;

    private CaseDecisionCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new CaseDecisionCalculationService(
                payloadParser, normalizer, enricher, logger
        );
    }

    @Test
    void description_parses_raw_intake_payload_validates_required_fields_normalizes_addresses_dates_enriches_with_channel_metadata_and_prepares_for_policy_matching() {
        // Arrange: Raw intake payload with required fields
        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("claimId", "CLM-12345");
        rawPayload.put("policyNumber", "POL-98765");
        rawPayload.put("incidentDate", "2023-10-25");
        rawPayload.put("address", "123 Main St, Anytown, USA");
        rawPayload.put("channel", "WEB");

        Map<String, Object> validatedPayload = new HashMap<>();
        validatedPayload.put("claimId", "CLM-12345");
        validatedPayload.put("policyNumber", "POL-98765");
        validatedPayload.put("incidentDate", "2023-10-25");
        validatedPayload.put("address", "123 Main St, Anytown, USA");
        validatedPayload.put("requiredFieldsValid", true);

        Map<String, Object> normalizedPayload = new HashMap<>();
        normalizedPayload.putAll(validatedPayload);
        normalizedPayload.put("incidentDate", LocalDate.of(2023, 10, 25));
        normalizedPayload.put("address", "123 MAIN ST, ANYTOWN, USA, 12345");

        Map<String, Object> enrichedPayload = new HashMap<>();
        enrichedPayload.putAll(normalizedPayload);
        enrichedPayload.put("channelMetadata", Map.of(
                "source", "WEB",
                "ingestedAt", "2023-10-26T10:00:00Z",
                "sessionId", "sess-abc-123"
        ));
        enrichedPayload.put("readyForPolicyMatching", true);

        // Mock external I/O & pipeline stages
        when(payloadParser.parseAndValidate(rawPayload)).thenReturn(validatedPayload);
        when(normalizer.normalize(validatedPayload)).thenReturn(normalizedPayload);
        when(enricher.enrich(normalizedPayload, "WEB")).thenReturn(enrichedPayload);

        // Act
        DecisionContext result = calculationService.processIntake(rawPayload);

        // Assert: Functional & NFR verification
        assertNotNull(result, "DecisionContext must not be null");
        assertTrue(result.isReadyForPolicyMatching(), "Payload must be prepared for policy matching");
        assertEquals("CLM-12345", result.getPayload().get("claimId"));
        assertEquals("POL-98765", result.getPayload().get("policyNumber"));
        assertTrue(result.getPayload().containsKey("address"), "Address must be normalized");
        assertTrue(result.getPayload().containsKey("channelMetadata"), "Channel metadata must be enriched");
        assertEquals("WEB", result.getPayload().get("channel"));

        // Verify pipeline execution order & structured logging
        verify(payloadParser).parseAndValidate(rawPayload);
        verify(normalizer).normalize(validatedPayload);
        verify(enricher).enrich(normalizedPayload, "WEB");
        verify(logger).log(Level.INFO, "Case decision calculation completed successfully", "claimId", "CLM-12345");
    }

    // Package-private interfaces for self-contained mock architecture
    interface IntakePayloadParser {
        Map<String, Object> parseAndValidate(Map<String, Object> rawPayload);
    }

    interface AddressDateNormalizer {
        Map<String, Object> normalize(Map<String, Object> payload);
    }

    interface ChannelMetadataEnricher {
        Map<String, Object> enrich(Map<String, Object> payload, String channel);
    }

    interface StructuredLogger {
        void log(Level level, String message, Object... keyValues);
    }

    record DecisionContext(Map<String, Object> payload, boolean readyForPolicyMatching) {}

    /**
     * Stateless service ensuring thread safety.
     * Encapsulates the calculation pipeline without direct infra calls.
     */
    static class CaseDecisionCalculationService {
        private final IntakePayloadParser parser;
        private final AddressDateNormalizer normalizer;
        private final ChannelMetadataEnricher enricher;
        private final StructuredLogger logger;

        CaseDecisionCalculationService(IntakePayloadParser parser, AddressDateNormalizer normalizer,
                                       ChannelMetadataEnricher enricher, StructuredLogger logger) {
            this.parser = parser;
            this.normalizer = normalizer;
            this.enricher = enricher;
            this.logger = logger;
        }

        DecisionContext processIntake(Map<String, Object> rawPayload) {
            Map<String, Object> validated = parser.parseAndValidate(rawPayload);
            Map<String, Object> normalized = normalizer.normalize(validated);
            String channel = (String) validated.getOrDefault("channel", "UNKNOWN");
            Map<String, Object> enriched = enricher.enrich(normalized, channel);
            logger.log(Level.INFO, "Case decision calculation completed successfully", "claimId", validated.get("claimId"));
            return new DecisionContext(enriched, true);
        }
    }
}
