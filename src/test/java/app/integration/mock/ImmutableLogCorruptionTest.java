package app.integration.mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImmutableLogCorruptionTest {

    @Mock
    private ImmutableLogReader logReader;

    @Mock
    private StructuredLogger auditLogger;

    @Mock
    private DecisionTransformationService transformationService;

    @InjectMocks
    private ImmutableLogProcessor processor;

    @BeforeEach
    void setUp() {
        reset(logReader, auditLogger, transformationService);
    }

    @Test
    void immutable_log_corruption() {
        // Given: A corrupted log entry (e.g., invalid checksum or malformed payload)
        String corruptedPayload = "{\"exposureId\":\"exp-123\",\"amount\":\"not_a_number\",\"currency\":\"USD\"}";
        ImmutableLogEntry corruptedEntry = new ImmutableLogEntry(
                "log-entry-001",
                Instant.now().toString(),
                corruptedPayload,
                "INVALID_CHECKSUM"
        );

        when(logReader.readNext()).thenReturn(Optional.of(corruptedEntry));

        // When: Processor attempts to handle the corrupted entry
        assertDoesNotThrow(() -> processor.processNextEntry());

        // Then: Verify structured error logging occurred (Observability NFR)
        verify(auditLogger).log(
                eq("CORRUPTED_LOG_ENTRY_DETECTED"),
                eq("ERROR"),
                eq("app.integration.mock"),
                any(Map.class)
        );

        // Then: Verify transformation was NOT triggered due to validation failure (Input Validation NFR)
        verifyNoInteractions(transformationService);

        // Then: Verify system continues without crashing (State consistency & thread safety)
        verify(logReader).readNext();
    }

    // Minimal domain classes for test isolation
    static class ImmutableLogEntry {
        private final String entryId;
        private final String timestamp;
        private final String payload;
        private final String checksum;

        ImmutableLogEntry(String entryId, String timestamp, String payload, String checksum) {
            this.entryId = entryId;
            this.timestamp = timestamp;
            this.payload = payload;
            this.checksum = checksum;
        }

        String entryId() { return entryId; }
        String timestamp() { return timestamp; }
        String payload() { return payload; }
        String checksum() { return checksum; }
    }

    interface ImmutableLogReader {
        Optional<ImmutableLogEntry> readNext();
    }

    interface StructuredLogger {
        void log(String eventCode, String level, String source, Map<String, Object> context);
    }

    interface DecisionTransformationService {
        void transform(ImmutableLogEntry entry);
    }

    class ImmutableLogProcessor {
        private final ImmutableLogReader logReader;
        private final StructuredLogger auditLogger;
        private final DecisionTransformationService transformationService;

        ImmutableLogProcessor(ImmutableLogReader logReader, StructuredLogger auditLogger, DecisionTransformationService transformationService) {
            this.logReader = logReader;
            this.auditLogger = auditLogger;
            this.transformationService = transformationService;
        }

        void processNextEntry() {
            var entry = logReader.readNext().orElse(null);
            if (entry == null) return;

            if (!isValidEntry(entry)) {
                auditLogger.log(
                        "CORRUPTED_LOG_ENTRY_DETECTED",
                        "ERROR",
                        "app.integration.mock",
                        Map.of("entryId", entry.entryId(), "reason", "checksum_mismatch_or_malformed_payload")
                );
                return;
            }

            transformationService.transform(entry);
        }

        private boolean isValidEntry(ImmutableLogEntry entry) {
            // Simulate validation logic (checksum, JSON parse, field constraints)
            if (entry.checksum() == null || !entry.checksum().startsWith("VALID_")) {
                return false;
            }
            try {
                var json = entry.payload();
                if (!json.contains("exposureId") || !json.contains("amount")) {
                    return false;
                }
                Double.parseDouble(json.substring(json.indexOf("\"amount\":\"") + 10).split("\"")[0]);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
