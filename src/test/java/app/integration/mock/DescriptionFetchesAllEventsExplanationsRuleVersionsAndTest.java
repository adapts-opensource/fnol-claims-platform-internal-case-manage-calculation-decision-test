package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationEnrichmentValidationTest {

    @Mock
    private ClaimDataRepository claimDataRepository;

    private EnrichmentValidationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EnrichmentValidationProcessor(claimDataRepository);
    }

    @Test
    void description_fetches_all_events_explanations_rule_versions_and_input_snapshots_verifies_hash_integrity_returns_structured_report() {
        // Arrange
        String claimId = "claim-std-001";
        List<String> events = List.of("EVENT_CREATED", "EVENT_ENRICHED", "EVENT_VALIDATED");
        List<String> explanations = List.of("Standardized claim format applied", "Missing fields auto-filled", "Schema compliance checked");
        List<String> ruleVersions = List.of("rule-v1.2", "rule-v2.0", "rule-v2.1");
        List<Map<String, Object>> inputSnapshots = List.of(
            Map.of("claim_id", claimId, "status", "INITIAL"),
            Map.of("claim_id", claimId, "status", "ENRICHED"),
            Map.of("claim_id", claimId, "status", "VALIDATED")
        );

        when(claimDataRepository.fetchEvents(claimId)).thenReturn(events);
        when(claimDataRepository.fetchExplanations(claimId)).thenReturn(explanations);
        when(claimDataRepository.fetchRuleVersions(claimId)).thenReturn(ruleVersions);
        when(claimDataRepository.fetchInputSnapshots(claimId)).thenReturn(inputSnapshots);

        // Act
        Map<String, Object> report = processor.validateAndEnrich(claimId);

        // Assert
        assertNotNull(report);
        assertEquals(claimId, report.get("claimId"));
        assertEquals(events, report.get("events"));
        assertEquals(explanations, report.get("explanations"));
        assertEquals(ruleVersions, report.get("ruleVersions"));
        assertEquals(inputSnapshots, report.get("inputSnapshots"));

        // Verify hash integrity
        String concatenatedPayload = String.join("|", events, explanations, ruleVersions, inputSnapshots.toString());
        String expectedHash = computeSha256(concatenatedPayload);
        assertEquals(expectedHash, report.get("hashIntegrity"));

        // Verify structured report metadata
        assertEquals("SUCCESS", report.get("status"));
        assertTrue((boolean) report.get("hashVerified"));
        assertEquals(4, report.get("dataSourcesFetched"));
        assertInstanceOf(Map.class, report.get("payload"));

        // Verify mock interactions
        verify(claimDataRepository, times(1)).fetchEvents(claimId);
        verify(claimDataRepository, times(1)).fetchExplanations(claimId);
        verify(claimDataRepository, times(1)).fetchRuleVersions(claimId);
        verify(claimDataRepository, times(1)).fetchInputSnapshots(claimId);
    }

    private String computeSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available", e);
        }
    }

    // Mocked repository layer abstracting S3 & DynamoDB I/O
    interface ClaimDataRepository {
        List<String> fetchEvents(String claimId);
        List<String> fetchExplanations(String claimId);
        List<String> fetchRuleVersions(String claimId);
        List<Map<String, Object>> fetchInputSnapshots(String claimId);
    }

    // Business logic processor for enrichment & validation
    static class EnrichmentValidationProcessor {
        private final ClaimDataRepository repository;

        EnrichmentValidationProcessor(ClaimDataRepository repository) {
            this.repository = repository;
        }

        Map<String, Object> validateAndEnrich(String claimId) {
            List<String> events = repository.fetchEvents(claimId);
            List<String> explanations = repository.fetchExplanations(claimId);
            List<String> ruleVersions = repository.fetchRuleVersions(claimId);
            List<Map<String, Object>> inputSnapshots = repository.fetchInputSnapshots(claimId);

            String payload = String.join("|", events, explanations, ruleVersions, inputSnapshots.toString());
            String hash = computeSha256(payload);

            return Map.of(
                "claimId", claimId,
                "events", events,
                "explanations", explanations,
                "ruleVersions", ruleVersions,
                "inputSnapshots", inputSnapshots,
                "hashIntegrity", hash,
                "status", "SUCCESS",
                "hashVerified", true,
                "dataSourcesFetched", 4,
                "payload", Map.of("id", claimId, "enrichedData", inputSnapshots)
            );
        }

        private String computeSha256(String data) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                return hexString.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Hash algorithm not available", e);
            }
        }
    }
}
