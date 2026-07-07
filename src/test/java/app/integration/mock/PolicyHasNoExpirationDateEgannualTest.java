package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private ClaimDecisionTransformationService transformationService;

    @Mock
    private ClaimDataIngestionService ingestionService;

    private String claimId;
    private Map<String, Object> rawClaimPayload;

    @BeforeEach
    void setUp() {
        claimId = "CLM-2024-ANNUAL-001";
        rawClaimPayload = Map.of(
            "policyType", "ANNUAL",
            "policyExpirationDate", null,
            "policyStartDate", "2024-01-01T00:00:00Z",
            "coverageStatus", "ACTIVE",
            "premiumFrequency", "YEARLY"
        );
    }

    @Test
    void policyHasNoExpirationDateEGAnnualPolicyNotYetExpired() {
        // Arrange: Mock external data ingestion
        when(ingestionService.fetchClaimData(claimId)).thenReturn(Optional.of(rawClaimPayload));

        // Arrange: Define expected standardized output for no-expiration annual policy
        Map<String, Object> expectedTransformedPayload = Map.of(
            "id", claimId,
            "standardizedPolicyType", "ANNUAL",
            "hasExpirationDate", false,
            "expirationDate", null,
            "isPolicyExpired", false,
            "transformationStatus", "SUCCESS",
            "decisionOutcome", "APPROVE_FOR_UNDERWRITING"
        );
        when(transformationService.applyDecisionTransformation(any(Map.class))).thenReturn(expectedTransformedPayload);

        // Act: Execute the transformation pipeline
        Map<String, Object> actualResult = transformationService.applyDecisionTransformation(rawClaimPayload);

        // Assert: Verify transformation correctly handles missing expiration date
        assertNotNull(actualResult, "Transformed payload must not be null");
        assertEquals(claimId, actualResult.get("id"));
        assertEquals(false, actualResult.get("isPolicyExpired"));
        assertNull(actualResult.get("expirationDate"));
        assertEquals(false, actualResult.get("hasExpirationDate"));
        assertEquals("APPROVE_FOR_UNDERWRITING", actualResult.get("decisionOutcome"));

        // Verify external service interactions
        verify(transformationService, times(1)).applyDecisionTransformation(rawClaimPayload);
        verify(ingestionService, times(1)).fetchClaimData(claimId);
    }
}
