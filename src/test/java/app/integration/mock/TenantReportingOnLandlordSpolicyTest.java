package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private ValidationOrchestrationService validationService;
    @Mock
    private ClaimIntakeService intakeService;
    @Mock
    private DataStoreService dataStoreService;
    @Mock
    private CommunicationsHandlerService commsService;

    private TenantLandlordPolicyOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new TenantLandlordPolicyOrchestrationService(
            validationService, intakeService, dataStoreService, commsService
        );
    }

    @Test
    void tenant_reporting_on_landlord_s_policy() {
        // Arrange
        String submissionId = UUID.randomUUID().toString();
        String landlordPolicyId = "pol-landlord-999";
        String tenantId = "ten-tenant-888";
        Map<String, Object> payload = Map.of(
            "claimType", "PROPERTY_DAMAGE",
            "reportedBy", "TENANT",
            "policyId", landlordPolicyId,
            "tenantId", tenantId,
            "incidentDate", "2024-05-15T09:30:00Z",
            "description", "Burst pipe in adjacent unit",
            "authorizationProof", "lease_agreement_signed.pdf"
        );

        when(validationService.validate(anyString(), anyMap()))
            .thenReturn(ValidationResult.success(submissionId));
        when(intakeService.storeSubmission(anyString(), anyString()))
            .thenReturn("s3://Claim-Intake-Service-bucket/" + submissionId + ".json");
        when(dataStoreService.saveStateTransition(anyString(), anyMap()))
            .thenReturn(Map.of("id", submissionId, "payload", payload));
        when(commsService.sendNotification(anyString(), anyList(), anyString()))
            .thenReturn("ses-msg-001");

        // Act
        FnolSubmissionResult result = orchestrationService.processSubmission(submissionId, payload);

        // Assert
        assertNotNull(result, "Submission result should not be null");
        assertEquals(submissionId, result.submissionId(), "Submission ID must match");
        assertTrue(result.isValid(), "Validation should pass for authorized tenant reporting");
        assertEquals("s3://Claim-Intake-Service-bucket/" + submissionId + ".json", result.intakeUri(), "S3 URI must be resolved");

        // Verify orchestration flow
        verify(validationService, times(1)).validate(eq(submissionId), eq(payload));
        verify(intakeService, times(1)).storeSubmission(eq(submissionId), eq("application/json"));
        verify(dataStoreService, times(1)).saveStateTransition(eq(submissionId), anyMap());
        verify(commsService, times(1)).sendNotification(
            eq("landlord@newco-insurance.com"),
            eq(List.of("claims@newco-insurance.com", "tenant@newco-insurance.com")),
            eq("us-east-1")
        );
    }
}
