package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationEnrichmentDecisionMockTest {

    @Mock
    private EnrichmentDecisionProcessor enrichmentProcessor;

    @Mock
    private AcknowledgmentDelivery acknowledgmentDelivery;

    @Mock
    private ClaimDataStore claimDataStore;

    private ClaimDecisionEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimDecisionEnrichmentService(enrichmentProcessor, acknowledgmentDelivery, claimDataStore);
    }

    @Test
    void acknowledgment_delivered_within_5_minutes() {
        String claimId = "claim-std-001";
        Map<String, Object> payload = Map.of("status", "ENRICHED", "decision", "APPROVED");

        Instant startTime = Instant.now();

        enrichmentService.executeEnrichmentAndDecision(claimId, payload);

        Instant endTime = Instant.now();
        Duration elapsed = Duration.between(startTime, endTime);

        assertTrue(elapsed.compareTo(Duration.ofMinutes(5)) <= 0,
                "Acknowledgment must be delivered within 5 minutes. Actual duration: " + elapsed);

        verify(enrichmentProcessor).standardizeAndEnrich(eq(claimId), any());
        verify(claimDataStore).persist(eq(claimId), any());
        verify(acknowledgmentDelivery).sendAcknowledgment(eq(claimId), any());
    }

    interface EnrichmentDecisionProcessor {
        void standardizeAndEnrich(String claimId, Map<String, Object> payload);
    }

    interface AcknowledgmentDelivery {
        void sendAcknowledgment(String claimId, Map<String, Object> metadata);
    }

    interface ClaimDataStore {
        void persist(String claimId, Map<String, Object> data);
    }

    static class ClaimDecisionEnrichmentService {
        private final EnrichmentDecisionProcessor processor;
        private final AcknowledgmentDelivery delivery;
        private final ClaimDataStore dataStore;

        ClaimDecisionEnrichmentService(EnrichmentDecisionProcessor processor,
                                       AcknowledgmentDelivery delivery,
                                       ClaimDataStore dataStore) {
            this.processor = processor;
            this.delivery = delivery;
            this.dataStore = dataStore;
        }

        void executeEnrichmentAndDecision(String claimId, Map<String, Object> payload) {
            processor.standardizeAndEnrich(claimId, payload);
            dataStore.persist(claimId, payload);
            delivery.sendAcknowledgment(claimId, Map.of("status", "DELIVERED"));
        }
    }
}
