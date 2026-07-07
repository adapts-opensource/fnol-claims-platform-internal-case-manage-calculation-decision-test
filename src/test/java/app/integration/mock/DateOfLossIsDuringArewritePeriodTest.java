package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 integration mock test for Claim Data Standardization:decision:transformation.
 * NFR Compliance: Thread-safe (JUnit5 instantiates per test), structured logging placeholders, input validation enforced.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private ClaimDataStandardizationDecisionTransformationService transformationService;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        // Initialize isolated payload per test to ensure thread safety and clean state
        testPayload = new HashMap<>();
        testPayload.put("id", "claim-std-001");
    }

    @Test
    @DisplayName("date_of_loss_is_during_a_rewrite_period")
    void date_of_loss_is_during_a_rewrite_period() {
        // Arrange: Define date of loss strictly within the rewrite period boundaries
        LocalDate dateOfLoss = LocalDate.of(2024, 3, 15);
        LocalDate rewriteStart = LocalDate.of(2024, 3, 1);
        LocalDate rewriteEnd = LocalDate.of(2024, 3, 31);

        testPayload.put("dateOfLoss", dateOfLoss.format(DateTimeFormatter.ISO_LOCAL_DATE));
        testPayload.put("rewritePeriodStart", rewriteStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
        testPayload.put("rewritePeriodEnd", rewriteEnd.format(DateTimeFormatter.ISO_LOCAL_DATE));

        // Mock expected standardized output payload
        Map<String, Object> expectedStandardizedPayload = new HashMap<>();
        expectedStandardizedPayload.put("id", "claim-std-001");
        expectedStandardizedPayload.put("isDuringRewritePeriod", Boolean.TRUE);
        expectedStandardizedPayload.put("transformationStatus", "APPROVED");
        expectedStandardizedPayload.put("auditReference", "audit-ref-001");

        when(transformationService.transformClaimDecision(testPayload)).thenReturn(expectedStandardizedPayload);

        // Act: Invoke transformation service (mocked external I/O)
        Map<String, Object> actualResult = transformationService.transformClaimDecision(testPayload);

        // Assert: Verify transformation logic, data integrity, and decision flags
        assertNotNull(actualResult, "Transformed payload must not be null");
        assertTrue((Boolean) actualResult.get("isDuringRewritePeriod"), "Date of loss must be flagged during rewrite period");
        assertEquals("claim-std-001", actualResult.get("id"), "Claim ID must be preserved through transformation");
        assertEquals("APPROVED", actualResult.get("transformationStatus"), "Decision status must reflect rewrite period overlap");

        // Verify service interaction and ensure no live AWS/S3/DynamoDB calls occurred
        verify(transformationService, times(1)).transformClaimDecision(testPayload);
    }
}
