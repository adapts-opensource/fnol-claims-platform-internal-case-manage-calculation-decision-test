package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MultipleMatchesTriggerManualReviewTest {

    @Mock
    private PolicyClaimsDbService policyClaimsDbService;
    @Mock
    private DocumentStorageService documentStorageService;
    @Mock
    private ComplianceAuditService complianceAuditService;
    @Mock
    private ClaimRoutingService claimRoutingService;

    @InjectMocks
    private ClaimTransformationOrchestrator claimTransformationOrchestrator;

    @BeforeEach
    void setUp() {
        // Reset mocks and ensure deterministic state for each test execution
        clearInvocations(policyClaimsDbService, documentStorageService, complianceAuditService, claimRoutingService);
    }

    @Test
    void multipleMatchesTriggerManualReview() {
        // Arrange
        String claimId = "CLM-2024-001";
        ClaimInitiationDto claimDto = new ClaimInitiationDto(claimId, "VEHICLE_COLLISION");

        // Simulate multiple policy matches from DynamoDB lookup
        List<PolicyMatchDto> multipleMatches = Arrays.asList(
            new PolicyMatchDto("POL-8842", "ACTIVE"),
            new PolicyMatchDto("POL-9910", "ACTIVE")
        );
        when(policyClaimsDbService.queryMatches(claimId, claimDto.getEventType())).thenReturn(multipleMatches);

        // Mock S3 integrations to prevent live calls and satisfy NFRs (compliance, observability)
        when(documentStorageService.persistPayload(claimId, anyMap())).thenReturn("s3://DocumentStorage-bucket/CLM-2024-001.json");
        when(complianceAuditService.logEvent(claimId, anyMap())).thenReturn("s3://ComplianceAuditService-bucket/audit.json");

        // Act
        TransformationResult result = claimTransformationOrchestrator.execute(claimDto);

        // Assert
        assertEquals(RoutingStatus.MANUAL_REVIEW, result.getRoutingStatus());
        verify(claimRoutingService, times(1)).assignToManualQueue(claimId, multipleMatches);
        verify(policyClaimsDbService, times(1)).queryMatches(claimId, "VEHICLE_COLLISION");
        verify(documentStorageService, times(1)).persistPayload(eq(claimId), anyMap());
        verify(complianceAuditService, times(1)).logEvent(eq(claimId), anyMap());
    }

    // Minimal DTO/Enum definitions for compilation context
    static class ClaimInitiationDto {
        private final String claimId;
        private final String eventType;
        ClaimInitiationDto(String claimId, String eventType) { this.claimId = claimId; this.eventType = eventType; }
        String getEventType() { return eventType; }
    }

    static class PolicyMatchDto {
        private final String policyId;
        private final String status;
        PolicyMatchDto(String policyId, String status) { this.policyId = policyId; this.status = status; }
    }

    static class TransformationResult {
        private final RoutingStatus routingStatus;
        TransformationResult(RoutingStatus routingStatus) { this.routingStatus = routingStatus; }
        RoutingStatus getRoutingStatus() { return routingStatus; }
    }

    // Minimal interface stubs for mock targets
    interface PolicyClaimsDbService { List<PolicyMatchDto> queryMatches(String claimId, String eventType); }
    interface DocumentStorageService { String persistPayload(String claimId, Map<String, Object> payload); }
    interface ComplianceAuditService { String logEvent(String claimId, Map<String, Object> payload); }
    interface ClaimRoutingService { void assignToManualQueue(String claimId, List<PolicyMatchDto> matches); }

    // Service under test
    @SuppressWarnings("unused")
    static class ClaimTransformationOrchestrator {
        private final PolicyClaimsDbService policyClaimsDbService;
        private final DocumentStorageService documentStorageService;
        private final ComplianceAuditService complianceAuditService;
        private final ClaimRoutingService claimRoutingService;

        ClaimTransformationOrchestrator(PolicyClaimsDbService policyClaimsDbService, DocumentStorageService documentStorageService, ComplianceAuditService complianceAuditService, ClaimRoutingService claimRoutingService) {
            this.policyClaimsDbService = policyClaimsDbService;
            this.documentStorageService = documentStorageService;
            this.complianceAuditService = complianceAuditService;
            this.claimRoutingService = claimRoutingService;
        }

        TransformationResult execute(ClaimInitiationDto dto) {
            List<PolicyMatchDto> matches = policyClaimsDbService.queryMatches(dto.claimId, dto.getEventType());
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("claimId", dto.claimId);
            payload.put("matches", matches);

            documentStorageService.persistPayload(dto.claimId, payload);
            complianceAuditService.logEvent(dto.claimId, payload);

            if (matches.size() > 1) {
                claimRoutingService.assignToManualQueue(dto.claimId, matches);
                return new TransformationResult(RoutingStatus.MANUAL_REVIEW);
            }
            return new TransformationResult(RoutingStatus.AUTO_APPROVED);
        }
    }
}
