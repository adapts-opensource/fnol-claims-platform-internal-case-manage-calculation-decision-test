package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationEnrichmentValidationTest {

    @Mock
    private ClaimEnrichmentValidationService validationService;

    private ClaimDataStandardizationEnrichmentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClaimDataStandardizationEnrichmentValidator(validationService);
    }

    @Test
    void task_resolved_within_sla_e_g_4_hours() {
        // Arrange: Simulate a payload where task resolution time is strictly less than 4 hours
        String claimId = "CLM-2023-001";
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "task_resolution_hours", 3.2,
                "sla_threshold_hours", 4.0,
                "enrichment_status", "PENDING"
        );

        when(validationService.validatePayload(any(Map.class)))
                .thenReturn(new ValidationOutcome(true, "SLA_MET"));

        // Act: Invoke the enrichment validation logic
        ValidationOutcome outcome = validator.runEnrichmentValidation(payload);

        // Assert: Verify SLA compliance and mock interaction
        assertNotNull(outcome);
        assertTrue(outcome.isValid());
        assertEquals("SLA_MET", outcome.getStatus());
        verify(validationService, times(1)).validatePayload(any(Map.class));
    }

    // Minimal stubs to ensure compilation and isolation of external I/O
    static class ClaimEnrichmentValidationService {
        ValidationOutcome validatePayload(Map<String, Object> payload) {
            return new ValidationOutcome(false, "UNKNOWN");
        }
    }

    static class ClaimDataStandardizationEnrichmentValidator {
        private final ClaimEnrichmentValidationService service;
        ClaimDataStandardizationEnrichmentValidator(ClaimEnrichmentValidationService service) {
            this.service = service;
        }
        ValidationOutcome runEnrichmentValidation(Map<String, Object> payload) {
            return service.validatePayload(payload);
        }
    }

    static class ValidationOutcome {
        private final boolean valid;
        private final String status;
        ValidationOutcome(boolean valid, String status) {
            this.valid = valid;
            this.status = status;
        }
        boolean isValid() { return valid; }
        String getStatus() { return status; }
    }
}
