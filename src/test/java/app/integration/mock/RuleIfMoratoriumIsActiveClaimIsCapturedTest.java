package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private MoratoriumCheckService moratoriumCheckService;

    @Mock
    private ClaimCaptureService claimCaptureService;

    @Mock
    private TaskRouterService taskRouterService;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void rule_if_moratorium_is_active_claim_is_captured_but_routed_to_coverage_review_n() {
        // Given
        String claimId = "CLM-MOR-001";
        Map<String, Object> rawClaimData = Map.of(
                "claimId", claimId,
                "type", "FNOL",
                "moratoriumActive", true,
                "status", "SUBMITTED"
        );

        when(moratoriumCheckService.isMoratoriumActive(claimId)).thenReturn(true);

        // When
        Map<String, Object> transformedClaim = decisionTransformationService.transformAndRoute(rawClaimData);

        // Then
        verify(moratoriumCheckService).isMoratoriumActive(claimId);
        verify(claimCaptureService).captureClaim(rawClaimData);
        verify(taskRouterService).routeTask(eq(claimId), eq("COVERAGE_REVIEW"));

        assertNotNull(transformedClaim);
        assertEquals("COVERAGE_REVIEW", transformedClaim.get("nextStage"));
        assertTrue(((Map<String, Object>) transformedClaim.get("metadata")).containsKey("moratoriumApplied"));
    }
}
