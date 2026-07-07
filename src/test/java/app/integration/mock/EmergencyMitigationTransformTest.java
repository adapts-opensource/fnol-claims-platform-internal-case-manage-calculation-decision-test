package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for Claim Initiation & Routing: calculation: transformation.
 * Verifies emergency mitigation task creation and routing logic.
 */
@ExtendWith(MockitoExtension.class)
class EmergencyMitigationTransformTest {

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private TaskService taskService;

    @Mock
    private VendorInspectionService vendorInspectionService;

    @InjectMocks
    private ClaimTransformationService claimTransformationService;

    @Captor
    private ArgumentCaptor<ClaimInitiationRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<TaskCreationPayload> taskCaptor;

    @BeforeEach
    void setUp() {
        // Reset mocks between tests
        clearInvocations(rulesEngineService, documentStoreService, taskService, vendorInspectionService);
    }

    @Test
    void transform_to_emergency_mitigation_on_water_fire() {
        // Arrange
        String tenantCode = "FL01";
        int year = 2024;
        String causeOfLoss = "water";
        boolean emergencyMitigationPerformed = true;
        String mitigationType = "tarping";
        String dateOfLoss = "2024-09-01";

        ClaimInitiationRequest request = new ClaimInitiationRequest(
                tenantCode, year, causeOfLoss, emergencyMitigationPerformed, mitigationType, dateOfLoss
        );

        String expectedClaimNumber = "CLM-2024-FL01-001";
        String expectedTaskId = "TASK-EM-2024-001";
        String expectedVendorRequestId = "VENDOR-REQ-001";

        // Mock Rules Engine to indicate emergency mitigation is required
        when(rulesEngineService.evaluate(any(ClaimInitiationRequest.class)))
                .thenReturn(new DamageAssessment(true, "WATER_DAMAGE_HIGH_RISK"));

        // Mock Task Service to return generated task ID
        when(taskService.create(any(TaskCreationPayload.class)))
                .thenReturn(expectedTaskId);

        // Mock Vendor Service to return queued request ID
        when(vendorInspectionService.queueRemediation(any(VendorRequestPayload.class)))
                .thenReturn(expectedVendorRequestId);

        // Mock Document Store for audit logging
        when(documentStoreService.storeAudit(anyString(), any(Map.class)))
                .thenReturn("s3://audit-bucket/2024/FL01/audit-001.json");

        // Act
        ClaimTransformationResult result = claimTransformationService.transform(request);

        // Assert
        assertNotNull(result, "Transformation result should not be null");
        assertEquals(expectedClaimNumber, result.claimNumber(), "Claim number should be generated");
        assertEquals(ClaimType.STANDARD, result.claimType(), "Claim type should be Standard property claim");
        assertTrue(result.emergencyMitigationRequired(), "Emergency mitigation should be flagged");

        // Verify Task Creation
        verify(taskService, times(1)).create(taskCaptor.capture());
        List<TaskCreationPayload> taskPayloads = taskCaptor.getAllValues();
        assertEquals(1, taskPayloads.size(), "Should create exactly one emergency mitigation task");

        TaskCreationPayload createdTask = taskPayloads.get(0);
        assertEquals(expectedTaskId, createdTask.taskId(), "Task ID should match mock return");
        assertEquals("Emergency Mitigation Review", createdTask.taskName(), "Task name should be Emergency Mitigation Review");
        assertEquals(tenantCode, createdTask.tenantCode(), "Task should have correct tenant code");

        // Verify Vendor Queuing
        verify(vendorInspectionService, times(1)).queueRemediation(any(VendorRequestPayload.class));
        ArgumentCaptor<VendorRequestPayload> vendorCaptor = ArgumentCaptor.forClass(VendorRequestPayload.class);
        verify(vendorInspectionService).queueRemediation(vendorCaptor.capture());
        VendorRequestPayload vendorPayload = vendorCaptor.getValue();
        assertEquals("tarping", vendorPayload.mitigationType(), "Vendor request should reference tarping mitigation");
        assertTrue(vendorPayload.vendorInspectionQueued(), "Vendor inspection should be queued");

        // Verify Document Store Audit
        verify(documentStoreService, times(1)).storeAudit(anyString(), any(Map.class));

        // Result Consistency
        assertEquals(expectedTaskId, result.emergencyMitigationTaskId(), "Result should contain task ID");
        assertEquals(expectedVendorRequestId, result.vendorRequestId(), "Result should contain vendor request ID");
        assertTrue(result.vendorInspectionQueued(), "Result should indicate vendor inspection is queued");
    }

    // --- Stubbed Domain Models for Compilation Context ---
    // In a real project, these would be defined in src/main/java

    record ClaimInitiationRequest(String tenantCode, int year, String causeOfLoss,
                                  boolean emergencyMitigationPerformed,
                                  String mitigationType, String dateOfLoss) {}

    record DamageAssessment(boolean requiresEmergencyMitigation, String riskCode) {}

    record TaskCreationPayload(String taskId, String taskName, String tenantCode, String causeOfLoss) {}

    record VendorRequestPayload(String requestId, String mitigationType, boolean vendorInspectionQueued) {}

    record ClaimTransformationResult(String claimNumber, ClaimType claimType,
                                     boolean emergencyMitigationRequired,
                                     String emergencyMitigationTaskId,
                                     String vendorRequestId,
                                     boolean vendorInspectionQueued) {}

    enum ClaimType { STANDARD, FLOOD, FIRE, OTHER }

    interface RulesEngineService {
        DamageAssessment evaluate(ClaimInitiationRequest request);
    }

    interface DocumentStoreService {
        String storeAudit(String key, Map<String, Object> payload);
    }

    interface TaskService {
        String create(TaskCreationPayload payload);
    }

    interface VendorInspectionService {
        String queueRemediation(VendorRequestPayload payload);
    }

    interface ClaimTransformationService {
        ClaimTransformationResult transform(ClaimInitiationRequest request);
    }
}
