package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock tests for Claim Initiation & Routing: orchestration: transformation.
 * Verifies business logic, NFR compliance (audit, observability), and infrastructure mocking.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingOrchestrationTransformationMockTests {

    @Mock
    private BusinessDayCalculator businessDayCalculator;

    @Mock
    private NotificationService notificationService;

    @Mock
    private S3Client complianceAuditS3Client;

    @Mock
    private S3Client documentStorageS3Client;

    @Mock
    private DynamoDbClient policyClaimsDynamoDbClient;

    @Mock
    private ClaimTransformationEngine transformationEngine;

    @Spy
    private StructuredLogger logger;

    @InjectMocks
    private ClaimOrchestrationServiceImpl claimOrchestrationService;

    private static final int ACKNOWLEDGMENT_THRESHOLD_DAYS = 10;
    private static final String FEATURE_ID = "Claim Initiation & Routing";

    @BeforeEach
    void setUp() {
        // Reset spies to ensure clean state for structured logging verification
        reset(logger);
    }

    /**
     * Test Case Label: AcknowledgmentMustBeSentWithin10BusinessDays
     * Description: Acknowledgment must be sent within 10 business days (example threshold).
     * Verifies that when a claim is processed within the business day threshold,
     * the acknowledgment is routed, audit trails are written, and observability is maintained.
     */
    @Test
    void acknowledgment_must_be_sent_within_10BusinessDays_exampleThreshold() {
        // Arrange
        String claimId = "CLM-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate initiationDate = LocalDate.of(2023, 10, 2);
        LocalDate processingDate = LocalDate.of(2023, 10, 13); // Example: ~8 business days later

        Claim claim = new Claim();
        claim.setId(claimId);
        claim.setInitiationDate(initiationDate);
        claim.setChannel("WEB_PORTAL");
        claim.setPiiData("REDACTED_FOR_TEST"); // Simulates GDPR handling

        // Mock business day calculation to simulate threshold check
        when(businessDayCalculator.calculateBusinessDays(initiationDate, processingDate)).thenReturn(8L);
        when(businessDayCalculator.isWithinThreshold(initiationDate, processingDate, ACKNOWLEDGMENT_THRESHOLD_DAYS)).thenReturn(true);

        // Mock transformation to return a valid payload
        ClaimTransformationResult transformationResult = new ClaimTransformationResult(claimId, Map.of("status", "INITIATED"));
        when(transformationEngine.transform(any(Claim.class))).thenReturn(transformationResult);

        // Act
        claimOrchestrationService.processClaimInitiation(claim, processingDate);

        // Assert: Notification
        verify(notificationService).sendAcknowledgment(
                eq(claimId),
                eq(processingDate),
                any(AcknowledgmentTemplate.class)
        );

        // Assert: Infrastructure - S3 Audit (ComplianceAuditService)
        ArgumentCaptor<PutObjectRequest> auditRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(complianceAuditS3Client).putObject(
                auditRequestCaptor.capture(),
                any(ByteArrayInputStream.class)
        );
        PutObjectRequest capturedAuditRequest = auditRequestCaptor.getValue();
        assertNotNull(capturedAuditRequest.bucket());
        assertTrue(capturedAuditRequest.key().contains(claimId));

        // Assert: Infrastructure - DynamoDB (PolicyClaimsDB)
        ArgumentCaptor<UpdateItemRequest> dbRequestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(policyClaimsDynamoDbClient).updateItem(dbRequestCaptor.capture());
        UpdateItemRequest capturedDbRequest = dbRequestCaptor.getValue();
        assertEquals(claimId, capturedDbRequest.key().get("pk").s());

        // Assert: Observability - Structured Logging
        verify(logger).info(
                eq("Claim Initiation Processed"),
                eq(Map.of(
                        "claimId", claimId,
                        "feature", FEATURE_ID,
                        "businessDaysElapsed", 8L,
                        "threshold", ACKNOWLEDGMENT_THRESHOLD_DAYS,
                        "status", "SUCCESS"
                ))
        );

        // Assert: Security/Input Validation
        verify(transformationEngine).transform(argThat(c -> c.getPiiData() != null && c.getPiiData().equals("REDACTED_FOR_TEST")));
    }

    // --- Mock Infrastructure & Domain Objects for Compilation Context ---

    interface BusinessDayCalculator {
        long calculateBusinessDays(LocalDate start, LocalDate end);
        boolean isWithinThreshold(LocalDate start, LocalDate current, int thresholdDays);
    }

    interface NotificationService {
        void sendAcknowledgment(String claimId, LocalDate date, AcknowledgmentTemplate template);
    }

    enum AcknowledgmentTemplate {
        STANDARD, URGENT
    }

    interface ClaimTransformationEngine {
        ClaimTransformationResult transform(Claim claim);
    }

    record ClaimTransformationResult(String claimId, Map<String, Object> payload) {}

    static class Claim {
        private String id;
        private LocalDate initiationDate;
        private String channel;
        private String piiData;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public LocalDate getInitiationDate() { return initiationDate; }
        public void setInitiationDate(LocalDate initiationDate) { this.initiationDate = initiationDate; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getPiiData() { return piiData; }
        public void setPiiData(String piiData) { this.piiData = piiData; }
    }

    static class StructuredLogger {
        public void info(String message, Map<String, Object> context) {
            // Implementation hidden; used for verification
        }
    }

    static class ClaimOrchestrationServiceImpl {
        private final BusinessDayCalculator businessDayCalculator;
        private final NotificationService notificationService;
        private final S3Client complianceAuditS3Client;
        private final DynamoDbClient policyClaimsDynamoDbClient;
        private final ClaimTransformationEngine transformationEngine;
        private final StructuredLogger logger;

        public ClaimOrchestrationServiceImpl(
                BusinessDayCalculator businessDayCalculator,
                NotificationService notificationService,
                S3Client complianceAuditS3Client,
                DynamoDbClient policyClaimsDynamoDbClient,
                ClaimTransformationEngine transformationEngine,
                StructuredLogger logger) {
            this.businessDayCalculator = businessDayCalculator;
            this.notificationService = notificationService;
            this.complianceAuditS3Client = complianceAuditS3Client;
            this.policyClaimsDynamoDbClient = policyClaimsDynamoDbClient;
            this.transformationEngine = transformationEngine;
            this.logger = logger;
        }

        public void processClaimInitiation(Claim claim, LocalDate processingDate) {
            // Business Logic Simulation
            long daysElapsed = businessDayCalculator.calculateBusinessDays(claim.getInitiationDate(), processingDate);
            
            if (businessDayCalculator.isWithinThreshold(claim.getInitiationDate(), processingDate, ACKNOWLEDGMENT_THRESHOLD_DAYS)) {
                
                // Transformation
                ClaimTransformationResult result = transformationEngine.transform(claim);
                
                // Routing / Acknowledgment
                notificationService.sendAcknowledgment(claim.getId(), processingDate, AcknowledgmentTemplate.STANDARD);
                
                // Audit Trail (Compliance)
                String auditKey = String.format("ComplianceAuditService/%s.json", claim.getId());
                String auditPayload = String.format("{\"claimId\":\"%s\",\"action\":\"INIT\"}", claim.getId());
                complianceAuditS3Client.putObject(
                        PutObjectRequest.builder().bucket("ComplianceAuditService-bucket").key(auditKey).build(),
                        new ByteArrayInputStream(auditPayload.getBytes())
                );
                
                // State Persistence (PolicyClaimsDB)
                policyClaimsDynamoDbClient.updateItem(
                        UpdateItemRequest.builder()
                                .tableName("PolicyClaimsDB_table")
                                .key(Map.of("pk", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(claim.getId()).build()))
                                .updateExpression("SET #s = :s")
                                .expressionAttributeNames(Map.of("#s", "status"))
                                .expressionAttributeValues(Map.of(":s", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s("INITIATED").build()))
                                .build()
                );

                // Observability
                logger.info("Claim Initiation Processed", Map.of(
                        "claimId", claim.getId(),
                        "feature", "Claim Initiation & Routing",
                        "businessDaysElapsed", daysElapsed,
                        "threshold", ACKNOWLEDGMENT_THRESHOLD_DAYS,
                        "status", "SUCCESS"
                ));
            } else {
                logger.warn("Acknowledgment threshold exceeded", Map.of("claimId", claim.getId()));
            }
        }
    }
}
