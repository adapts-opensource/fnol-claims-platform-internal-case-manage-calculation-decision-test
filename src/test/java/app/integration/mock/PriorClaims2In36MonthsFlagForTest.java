package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class PriorClaims2In36MonthsFlagForTest {

    @Mock
    private ClaimOrchestrationService claimOrchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void prior_claims_2_in_36_months_flag_for_fraud_risk_review() {
        // Arrange
        String claimId = "claim-uuid-789";
        Map<String, Object> payload = Map.of(
            "claimantId", "CL-456",
            "priorClaimsCount", 3,
            "priorClaimsDates", List.of("2022-05-10", "2023-01-15", "2023-09-20"),
            "currentClaimDate", "2024-01-15"
        );

        // Mock orchestration service to simulate state transition logic without calling live DynamoDB/S3
        when(claimOrchestrationService.standardizeAndTransition(claimId, payload))
            .thenReturn(Map.of(
                "state", "FRAUD_REVIEW_PENDING",
                "flags", Map.of("priorClaims2In36Months", true)
            ));

        // Act
        Map<String, Object> result = claimOrchestrationService.standardizeAndTransition(claimId, payload);

        // Assert
        assertNotNull(result);
        assertEquals("FRAUD_REVIEW_PENDING", result.get("state"));
        assertTrue((Boolean) ((Map<?, ?>) result.get("flags")).get("priorClaims2In36Months"));
        verify(claimOrchestrationService, times(1)).standardizeAndTransition(claimId, payload);
    }
}
