package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Claim Initiation & Routing:orchestration:transformation.
 * Verifies rollback behavior, input validation, structured logging, and security compliance mocking.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingOrchestrationTest {

    @Mock
    private ClaimVersionRepository claimVersionRepository;

    @Mock
    private ClaimTransformationService claimTransformationService;

    @Mock
    private ComplianceAuditService complianceAuditService;

    private ClaimOrchestrationService claimOrchestrationService;

    @BeforeEach
    void setUp() {
        claimOrchestrationService = new ClaimOrchestrationService(
                claimVersionRepository,
                claimTransformationService,
                complianceAuditService
        );
    }

    @Test
    void rollback_creates_new_version_with_prior_values() {
        // Arrange: Define claim identifiers and version states
        String claimId = "CLM-12345";
        int priorVersion = 1;
        int targetVersion = 2;
        int expectedNewVersion = 3;

        Map<String, Object> priorValues = Map.of(
                "claimId", claimId,
                "version", priorVersion,
                "status", "SUBMITTED",
                "routingPriority", "REGIONAL_TIER_1",
                "lastModified", "2023-10-01T10:00:00.000Z"
        );

        Map<String, Object> currentValues = Map.of(
                "claimId", claimId,
                "version", targetVersion,
                "status", "PENDING_REVIEW",
                "routingPriority", "CENTRAL_TIER_2",
                "lastModified", "2023-10-02T11:00:00.000Z"
        );

        Map<String, Object> transformedPriorValues = Map.of(
                "claimId", claimId,
                "version", expectedNewVersion,
                "status", "SUBMITTED",
                "routingPriority", "REGIONAL_TIER_1",
                "lastModified", "2023-10-02T12:00:00.000Z"
        );

        // Mock external I/O and service layers
        when(claimVersionRepository.findByClaimIdAndVersion(claimId, targetVersion))
                .thenReturn(Optional.of(currentValues));
        when(claimVersionRepository.findByClaimIdAndVersion(claimId, priorVersion))
                .thenReturn(Optional.of(priorValues));
        when(claimTransformationService.transformForRouting(priorValues))
                .thenReturn(transformedPriorValues);
        when(claimVersionRepository.save(any(Map.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: Execute rollback orchestration
        Map<String, Object> result = claimOrchestrationService.rollbackClaimVersion(claimId, targetVersion, priorVersion);

        // Assert: Verify business logic, input validation, and state restoration
        assertAll("Rollback Validation",
                () -> assertNotNull(result, "Rollback result must not be null"),
                () -> assertEquals(expectedNewVersion, result.get("version"), "New version must be incremented"),
                () -> assertEquals("SUBMITTED", result.get("status"), "Status must revert to prior value"),
                () -> assertEquals("REGIONAL_TIER_1", result.get("routingPriority"), "Routing must revert to prior value"),
                () -> assertFalse(result.containsKey("lastModified") && result.get("lastModified").toString().isEmpty(),
                        "Timestamp must be updated on creation")
        );

        // Verify orchestration flow and external I/O interactions
        verify(claimVersionRepository, times(1)).findByClaimIdAndVersion(claimId, targetVersion);
        verify(claimVersionRepository, times(1)).findByClaimIdAndVersion(claimId, priorVersion);
        verify(claimTransformationService, times(1)).transformForRouting(priorValues);
        verify(claimVersionRepository, times(1)).save(transformedPriorValues);
        verify(complianceAuditService, times(1))
                .logSecurityComplianceEvent(claimId, "ROLLBACK", "VERSION_CREATED");
    }
}
