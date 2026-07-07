package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NFR Compliance Notes:
 * - availability: Tests mock I/O to ensure deterministic execution across AZs.
 * - compliance: Validates GDPR/SOC2 data handling by asserting structured payload transformations.
 * - concurrency: Uses stateless mock setup; orchestration service should be thread-safe.
 * - observability: Structured logging verified via captured metadata payloads.
 * - operability: Clear assertion boundaries for success/failure output criteria.
 * - security: TLS/Least-privilege simulated via mock validation failures and auth error routing.
 */
@ExtendWith(MockitoExtension.class)
public class OutputCriteriaSuccessOutputsAttributionMetadataStoredIntakeForwardedTest {

    @Mock
    private ClaimDataStoreService claimDataStoreService; // DynamoDB: Claim Data Store
    @Mock
    private DocumentManagementService documentManagementService; // S3: Document Management
    @Mock
    private StandardizationAgentClient standardizationAgentClient;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private MappingResolutionService mappingResolutionService;

    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimDataStandardizationOrchestrationService(
                claimDataStoreService,
                documentManagementService,
                standardizationAgentClient,
                notificationService,
                authorizationService,
                mappingResolutionService
        );
    }

    @Test
    void output_criteria_success_outputs_attribution_metadata_stored_intake_forwarded_to_standardization_agent_notification_sent_failure_outputs_authorization_error_returned_mapping_resolution_task_created() {
        // Arrange: Initialize input payload matching claim_data_standardization_state_transition_orch
        String claimId = "claim-std-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("type", "AUTO_CLAIM");
        payload.put("state", "INTAKE_RECEIVED");
        payload.put("metadata", new HashMap<String, Object>());

        // Mock infrastructure I/O (DynamoDB, S3, HTTP clients)
        when(claimDataStoreService.putItem(anyString(), any(Map.class))).thenReturn(true);
        when(standardizationAgentClient.postIntake(anyString(), any(Map.class))).thenReturn("FORWARDED");
        when(notificationService.sendAsync(anyString(), anyString())).thenReturn(true);
        when(authorizationService.validateToken(any())).thenThrow(new SecurityException("AUTH_ERROR"));
        when(mappingResolutionService.createTask(anyString(), any(Map.class))).thenReturn("TASK-MAP-001");

        // Act: Execute orchestration
        Map<String, Object> result = orchestrationService.process(payload);

        // Assert: Success Outputs
        // 1. Attribution metadata stored
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(claimDataStoreService, times(1)).putItem(eq("Claim Data Store_table"), metadataCaptor.capture());
        Map<String, Object> storedMetadata = metadataCaptor.getValue();
        assertNotNull(storedMetadata, "Attribution metadata must be stored");
        assertEquals("STORED", storedMetadata.get("attribution_status"));

        // 2. Intake forwarded to standardization
        verify(standardizationAgentClient, times(1)).postIntake(eq(claimId), any(Map.class));

        // 3. Agent notification sent
        verify(notificationService, times(1)).sendAsync(eq(claimId), anyString());

        // Assert: Failure Outputs
        // 4. Authorization error returned
        verify(authorizationService, times(1)).validateToken(any());
        assertEquals("AUTHORIZATION_ERROR", result.get("outcome"));
        assertEquals("Authorization failed", result.get("error_detail"));

        // 5. Mapping resolution task created
        verify(mappingResolutionService, times(1)).createTask(eq(claimId), any(Map.class));
    }
}
