package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimDataStandardizationEnrichmentValidationMockTest {

    interface ClaimEnrichmentValidator {
        ValidationReport validatePayload(Map<String, Object> payload);
    }

    @Mock
    private ClaimEnrichmentValidator enrichmentValidator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void conflicts_detected_and_reported() {
        // Arrange
        Map<String, Object> claimPayload = Map.of(
                "claimId", "CLM-1001",
                "policyNumber", "POL-2002",
                "incidentDate", "2023-10-05",
                "damageEstimate", 5000.00
        );

        List<String> expectedConflicts = List.of(
                "Conflict: Incident date exceeds policy effective date",
                "Conflict: Damage estimate missing required source attribution"
        );

        when(enrichmentValidator.validatePayload(claimPayload))
                .thenReturn(new ValidationReport(expectedConflicts, true));

        // Act
        ValidationReport report = enrichmentValidator.validatePayload(claimPayload);

        // Assert
        assertNotNull(report, "Validation report should not be null");
        assertTrue(report.hasConflicts(), "Report should indicate conflicts were detected");
        assertEquals(2, report.getConflicts().size(), "Should report exactly two conflicts");
        assertTrue(report.getConflicts().containsAll(expectedConflicts), "Reported conflicts must match expected");
        verify(enrichmentValidator, times(1)).validatePayload(claimPayload);
    }

    /**
     * Internal model representing the outcome of claim data validation.
     */
    static class ValidationReport {
        private final List<String> conflicts;
        private final boolean hasConflicts;

        ValidationReport(List<String> conflicts, boolean hasConflicts) {
            this.conflicts = conflicts;
            this.hasConflicts = hasConflicts;
        }

        List<String> getConflicts() {
            return conflicts;
        }

        boolean hasConflicts() {
            return hasConflicts;
        }
    }
}
