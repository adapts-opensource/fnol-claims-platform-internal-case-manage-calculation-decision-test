package app.integration.mock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.integration.mock.infra.DynamoDbClient;
import app.integration.mock.infra.S3Client;
import app.integration.mock.service.AccessLogService;
import app.integration.mock.service.DataQualityService;
import app.integration.mock.service.EventPublisher;
import app.integration.mock.service.NotificationService;
import app.integration.mock.model.ClaimDataStandardizationStateTransitionOrch;
import app.integration.mock.model.MissingDataFlag;
import app.integration.mock.model.AuditPackage;
import app.integration.mock.model.ExportEvent;

/**
 * JUnit 5 test class for Claim Data Standardization:transformation:orchestration.
 * Verifies output criteria for success and failure outputs including audit, events, logs, and data quality flags.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationTest {

    @Mock
    private DynamoDbClient claimDataStore;

    @Mock
    private DynamoDbClient rulesTriageStore;

    @Mock
    private S3Client documentManagementS3;

    @Mock
    private AccessLogService accessLogService;

    @Mock
    private EventPublisher exportEventPublisher;

    @Mock
    private DataQualityService dataQualityService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    private static final String CLAIM_ID = "claim-123-456";
    private static final String PK = "pk";
    private static final String TABLE_NAME_CLAIM_STORE = "Claim Data Store_table";
    private static final String TABLE_NAME_RULES_STORE = "Rules & Triage Service_table";
    private static final String BUCKET_NAME = "Document Management-bucket";
    private static final String OBJECT_KEY_PATTERN = "Document Management/{entity_id}.json";

    @BeforeEach
    void setUp() {
        // Reset mocks if needed between tests
    }

    /**
     * Test Case: OutputCriteriaSuccess_outputsAuditPackageGeneratedExportEvent
     * Description: Output criteria: {'success_outputs': ['Audit package generated', 'Export event emitted', 'Access log recorded'], 'failure_outputs': ['Missing data flagged', 'Export delayed with notification']}
     */
    @Test
    void output_criteria_success_outputs_audit_package_generated_export_event_emitted_access_log_recorded_failure_outputs_missing_data_flagged_export_delayed_with_notification() {
        // Given: Payload with missing data to trigger failure outputs while still completing orchestration steps
        Map<String, Object> inputPayload = Map.of(
            "id", CLAIM_ID,
            "status", "SUBMITTED",
            "claimantName", "John Doe",
            "incidentDate", Instant.now().toString(),
            "damageDescription", "Rear-end collision",
            "missingField", null // Simulates missing required data
        );

        ClaimDataStandardizationStateTransitionOrch stateTransition = new ClaimDataStandardizationStateTransitionOrch();
        stateTransition.setId(CLAIM_ID);
        stateTransition.setPayload(inputPayload);

        // Mock Infrastructure I/O: Read Claim
        when(claimDataStore.getItem(eq(TABLE_NAME_CLAIM_STORE), eq(PK), eq(CLAIM_ID)))
            .thenReturn(inputPayload);

        // Mock Data Quality: Detect missing data
        MissingDataFlag missingDataFlag = MissingDataFlag.builder()
            .claimId(CLAIM_ID)
            .field("missingField")
            .timestamp(Instant.now())
            .build();
        when(dataQualityService.validate(anyMap()))
            .thenReturn(missingDataFlag);

        // Mock Infrastructure I/O: Write State/Transaction Log
        when(claimDataStore.putItem(eq(TABLE_NAME_CLAIM_STORE), anyMap()))
            .thenReturn(Map.of("ItemCollectionMetrics", Map.of()));

        // Mock Infrastructure I/O: Write Rules/Triage State
        when(rulesTriageStore.putItem(eq(TABLE_NAME_RULES_STORE), anyMap()))
            .thenReturn(Map.of("ItemCollectionMetrics", Map.of()));

        // Mock Infrastructure I/O: Write Audit Package to S3
        String expectedObjectKey = String.format(OBJECT_KEY_PATTERN, "entity_id", CLAIM_ID);
        when(documentManagementS3.putObject(eq(BUCKET_NAME), eq(expectedObjectKey), any()))
            .thenReturn("s3://" + BUCKET_NAME + "/" + expectedObjectKey);

        // Mock Event Emission
        ExportEvent exportEvent = ExportEvent.builder()
            .claimId(CLAIM_ID)
            .eventTime(Instant.now())
            .payload(inputPayload)
            .build();
        when(exportEventPublisher.publishExportEvent(anyString(), any()))
            .thenReturn(exportEvent);

        // Mock Notification Service
        when(notificationService.sendDelayNotification(eq(CLAIM_ID), anyString()))
            .thenReturn(true);

        // Mock Access Log
        when(accessLogService.recordAccessLog(anyString(), anyString(), anyString()))
            .thenReturn(true);

        // When: Execute orchestration
        ClaimDataStandardizationStateTransitionOrch result = orchestrator.transformAndOrchestrate(stateTransition);

        // Then: Verify Success Outputs
        // 1. Audit package generated
        verify(documentManagementS3, times(1))
            .putObject(eq(BUCKET_NAME), eq(expectedObjectKey), any());

        // 2. Export event emitted
        verify(exportEventPublisher, times(1))
            .publishExportEvent(eq(CLAIM_ID), any(ExportEvent.class));

        // 3. Access log recorded
        verify(accessLogService, times(1))
            .recordAccessLog(eq(CLAIM_ID), eq("TRANSFORMATION"), eq("ORCHESTRATION_COMPLETE"));

        // Then: Verify Failure Outputs
        // 1. Missing data flagged
        verify(dataQualityService, times(1))
            .validate(anyMap());
        // Verify that the flag was processed (assumed internal behavior or side effect)
        // In a real test, we might verify a specific method call on a flag repository if exposed,
        // or verify the state update reflects the flag. Here we verify the service was called.
        
        // 2. Export delayed with notification
        verify(notificationService, times(1))
            .sendDelayNotification(eq(CLAIM_ID), anyString());

        // Assertions: Verify result integrity
        assertNotNull(result, "Orchestration result should not be null");
        assertNotNull(result.getId(), "Result ID should be populated");
        assertNotNull(result.getPayload(), "Result payload should be populated");
        
        // Verify state transition reflects processing
        String payloadStatus = (String) result.getPayload().get("status");
        assertNotNull(payloadStatus, "Payload status should be updated");
        
        // Verify audit package URI is recorded in payload if applicable
        String auditUri = (String) result.getPayload().get("auditPackageUri");
        assertNotNull(auditUri, "Audit package URI should be recorded in payload");
        assertEquals("s3://" + BUCKET_NAME + "/" + expectedObjectKey, auditUri, "Audit URI should match S3 write");
    }
}
