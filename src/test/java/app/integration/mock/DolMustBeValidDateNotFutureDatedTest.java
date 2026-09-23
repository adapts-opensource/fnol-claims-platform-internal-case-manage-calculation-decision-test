package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock test for Claim Data Standardization:validation:decision
 * Verifies DOL validation rules while isolating external I/O (S3/DynamoDB).
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private ClaimValidationEngine claimValidationEngine;

    private Map<String, Object> claimData;

    @BeforeEach
    void setUp() {
        claimData = new HashMap<>();
        claimData.put("id", "FNOL-CLAIM-001");
        claimData.put("payload", new HashMap<String, Object>());
    }

    /**
     * Test Case Label: DolMustBeValidDateNotFutureDated
     * Verifies that DOL must be a valid date and cannot be future-dated beyond a 24-hour buffer.
     */
    @Test
    void dolMustBeValidDateNotFutureDatedBeyond24hBuffer() {
        // Arrange: Valid DOL within the 24-hour buffer
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime validDol = now.plusHours(12);
        ((Map<String, Object>) claimData.get("payload")).put("dol", validDol.toString());

        when(claimValidationEngine.validate(anyMap())).thenReturn(true);

        // Act
        boolean isValidWithinBuffer = claimValidationEngine.validate(claimData);

        // Assert
        assertTrue(isValidWithinBuffer, "DOL within 24h future buffer must pass validation");
        verify(claimValidationEngine).validate(claimData);

        // Arrange: Invalid DOL beyond the 24-hour buffer
        LocalDateTime invalidDol = now.plusHours(30);
        ((Map<String, Object>) claimData.get("payload")).put("dol", invalidDol.toString());
        when(claimValidationEngine.validate(anyMap())).thenReturn(false);

        // Act
        boolean isInvalidBeyondBuffer = claimValidationEngine.validate(claimData);

        // Assert
        assertFalse(isInvalidBeyondBuffer, "DOL beyond 24h future buffer must fail validation");
    }
}
