package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AuditCapturesFullResolutionTraceTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ClaimDataStandardizationValidator claimDataStandardizationValidator;

    @Captor
    private ArgumentCaptor<ResolutionTrace> traceCaptor;

    @Test
    void audit_captures_full_resolution_trace() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "policyNumber", "POL-987654",
                "claimType", "AUTO",
                "submissionDate", "2023-10-01"
        );

        // Act
        claimDataStandardizationValidator.validateAndEnrich(payload);

        // Assert
        verify(auditService).captureTrace(traceCaptor.capture());
        ResolutionTrace capturedTrace = traceCaptor.getValue();

        assertNotNull(capturedTrace, "Audit trace should not be null");
        assertEquals(claimId, capturedTrace.getClaimId(), "Claim ID should match input");
        assertEquals("SUCCESS", capturedTrace.getStatus(), "Resolution status should be SUCCESS");
        assertNotNull(capturedTrace.getTimestamp(), "Timestamp should be captured");
        assertNotNull(capturedTrace.getFullResolutionDetails(), "Full resolution details should be present");
        assertEquals(payload, capturedTrace.getInputPayload(), "Input payload should be preserved");
    }

    // Internal data structure representing the audit trace
    public static class ResolutionTrace {
        private final String claimId;
        private final String status;
        private final Instant timestamp;
        private final Map<String, Object> inputPayload;
        private final Map<String, Object> fullResolutionDetails;

        public ResolutionTrace(String claimId, String status, Instant timestamp,
                               Map<String, Object> inputPayload, Map<String, Object> fullResolutionDetails) {
            this.claimId = claimId;
            this.status = status;
            this.timestamp = timestamp;
            this.inputPayload = inputPayload;
            this.fullResolutionDetails = fullResolutionDetails;
        }

        public String getClaimId() { return claimId; }
        public String getStatus() { return status; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, Object> getInputPayload() { return inputPayload; }
        public Map<String, Object> getFullResolutionDetails() { return fullResolutionDetails; }
    }

    // Service interface simulating external audit I/O (mocked)
    public interface AuditService {
        void captureTrace(ResolutionTrace trace);
    }

    // Service under test simulating Claim Data Standardization:enrichment:validation
    public static class ClaimDataStandardizationValidator {
        private final AuditService auditService;

        public ClaimDataStandardizationValidator(AuditService auditService) {
            this.auditService = auditService;
        }

        public Map<String, Object> validateAndEnrich(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            Map<String, Object> enrichedPayload = Map.copyOf(payload);
            enrichedPayload.put("standardized", true);
            enrichedPayload.put("validatedAt", Instant.now());

            ResolutionTrace trace = new ResolutionTrace(
                    claimId,
                    "SUCCESS",
                    Instant.now(),
                    payload,
                    enrichedPayload
            );
            auditService.captureTrace(trace);
            return enrichedPayload;
        }
    }
}
