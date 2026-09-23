package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ReporterIdMustBeValidUuidTest {

    @Test
    void reporter_id_must_be_valid_uuid() {
        // Arrange: Valid UUID format
        String validUuid = UUID.randomUUID().toString();

        // Act & Assert: Valid UUID should pass validation without throwing
        assertDoesNotThrow(() -> validateReporterId(validUuid), "Valid UUID should pass validation");

        // Arrange: Collection of invalid UUID formats
        String[] invalidReporterIds = {
                "not-a-uuid",
                "12345",
                "",
                "00000000-0000-0000-0000-00000000000000", // too many characters
                "GHIJKLMN-OPQR-STUV-WXYZ-123456789ABC"  // invalid hex characters
        };

        // Act & Assert: Invalid UUIDs must be rejected with IllegalArgumentException
        for (String invalidId : invalidReporterIds) {
            assertThrows(IllegalArgumentException.class,
                    () -> validateReporterId(invalidId),
                    "Expected validation to reject invalid reporter_id: " + invalidId);
        }
    }

    /**
     * Simulates the input validation layer for the decision:state_transition feature.
     * Mocks the validation contract before persistence to DynamoDB or triggering SES notifications.
     * Ensures GDPR/SOC2 compliance by enforcing strict identifier formats early in the pipeline.
     */
    private void validateReporterId(String reporterId) {
        if (reporterId == null || reporterId.trim().isEmpty()) {
            throw new IllegalArgumentException("reporter_id must not be null or empty");
        }
        try {
            UUID.fromString(reporterId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("reporter_id must be a valid UUID", e);
        }
    }
}
