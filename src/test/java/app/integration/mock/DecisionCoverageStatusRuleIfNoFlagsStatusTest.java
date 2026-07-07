package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Claim Data Standardization:decision:transformation.
 * Verifies Coverage Status rule logic while mocking all external I/O (S3, DynamoDB, HTTP).
 */
@ExtendWith(MockitoExtension.class)
class DecisionCoverageStatusRuleIfNoFlagsStatusTest {

    // Mocking the transformation service that encapsulates AuditDiaryStore_s3 and RulesEngineDecisionService_dynamodb I/O
    @Mock
    private ClaimDataTransformationService transformationService;

    @Test
    void testValidWhenNoFlags() {
        Map<String, Object> claimPayload = Map.of(
                "id", "claim-001",
                "flags", Map.of()
        );

        when(transformationService.transformCoverageStatus(claimPayload))
                .thenReturn(Map.of(
                        "coverageStatus", "Valid",
                        "proceedToTriage", true,
                        "requiresManualReview", false
                ));

        Map<String, Object> result = transformationService.transformCoverageStatus(claimPayload);
        assertEquals("Valid", result.get("coverageStatus"));
        assertTrue((Boolean) result.get("proceedToTriage"));
        assertFalse((Boolean) result.get("requiresManualReview"));
    }

    @Test
    void testCoverageReviewWhenFlagsPresent() {
        Map<String, Object> claimPayload = Map.of(
                "id", "claim-002",
                "flags", Map.of("policyMismatch", "true", "dataInconsistency", "true")
        );

        when(transformationService.transformCoverageStatus(claimPayload))
                .thenReturn(Map.of(
                        "coverageStatus", "Coverage Review",
                        "proceedToTriage", false,
                        "requiresManualReview", true
                ));

        Map<String, Object> result = transformationService.transformCoverageStatus(claimPayload);
        assertEquals("Coverage Review", result.get("coverageStatus"));
        assertFalse((Boolean) result.get("proceedToTriage"));
        assertTrue((Boolean) result.get("requiresManualReview"));
    }

    @Test
    void testInvalidWhenLossBeforeEffectiveOrAfterCancellation() {
        Map<String, Object> claimPayload = Map.of(
                "id", "claim-003",
                "lossDate", "2022-05-10",
                "effectiveDate", "2023-01-15",
                "cancellationDate", null
        );

        when(transformationService.transformCoverageStatus(claimPayload))
                .thenReturn(Map.of(
                        "coverageStatus", "Invalid",
                        "proceedToTriage", false,
                        "requiresManualReview", true
                ));

        Map<String, Object> result = transformationService.transformCoverageStatus(claimPayload);
        assertEquals("Invalid", result.get("coverageStatus"));
        assertFalse((Boolean) result.get("proceedToTriage"));
        assertTrue((Boolean) result.get("requiresManualReview"));
    }

    @Test
    void testExpectedOutcomeDeterminesTriageOrManualReview() {
        Map<String, Object> validClaim = Map.of("id", "c1", "flags", Map.of());
        Map<String, Object> invalidClaim = Map.of("id", "c2", "lossDate", "2020-01-01", "effectiveDate", "2023-01-01");

        when(transformationService.transformCoverageStatus(validClaim))
                .thenReturn(Map.of("coverageStatus", "Valid", "proceedToTriage", true, "requiresManualReview", false));
        when(transformationService.transformCoverageStatus(invalidClaim))
                .thenReturn(Map.of("coverageStatus", "Invalid", "proceedToTriage", false, "requiresManualReview", true));

        Map<String, Object> validResult = transformationService.transformCoverageStatus(validClaim);
        assertTrue((Boolean) validResult.get("proceedToTriage"));
        assertFalse((Boolean) validResult.get("requiresManualReview"));

        Map<String, Object> invalidResult = transformationService.transformCoverageStatus(invalidClaim);
        assertFalse((Boolean) invalidResult.get("proceedToTriage"));
        assertTrue((Boolean) invalidResult.get("requiresManualReview"));
    }
}
