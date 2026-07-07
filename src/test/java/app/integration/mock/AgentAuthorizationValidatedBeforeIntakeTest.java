package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationMockTest {

    private static final String CLAIM_DATA_STORE_TABLE = "Claim Data Store_table";
    private static final String PK_ATTRIBUTE = "pk";
    private static final String DOCUMENT_MANAGEMENT_BUCKET = "Document Management-bucket";
    private static final String RULES_TRIAGE_TABLE = "Rules & Triage Service_table";

    @Mock
    private Logger structuredLogger;

    @Mock
    private AgentAuthorizationService agentAuthorizationService;

    @Mock
    private IntakeProcessingService intakeProcessingService;

    @Mock
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @Mock
    private DynamoDbClient claimDataStoreClient;

    @Mock
    private S3Client documentManagementClient;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    private String testClaimId;
    private Map<String, Object> standardPayload;

    @BeforeEach
    void setUp() {
        testClaimId = "TEST-CLAIM-001";
        standardPayload = new HashMap<>();
        standardPayload.put("id", testClaimId);
        standardPayload.put("status", "INITIATED");
        standardPayload.put("agentId", "AGENT-VALID-123");
        standardPayload.put("region", "us-east-1");
    }

    @Test
    void agent_authorization_validated_before_intake() {
        // Arrange: Mock external I/O and service behaviors
        when(agentAuthorizationService.validate("AGENT-VALID-123"))
                .thenReturn(true);
        when(intakeProcessingService.processIntake(testClaimId, standardPayload))
                .thenReturn(Map.of("id", testClaimId, "status", "INTAKE_COMPLETED", "validatedBy", "SYSTEM"));
        when(claimDataStoreClient.putItem(CLAIM_DATA_STORE_TABLE, PK_ATTRIBUTE, anyString(), payloadCaptor.capture()))
                .thenReturn(true);
        when(documentManagementClient.putObject(DOCUMENT_MANAGEMENT_BUCKET, eq("Document Management/" + testClaimId + ".json"), any(byte[].class)))
                .thenReturn("s3://" + DOCUMENT_MANAGEMENT_BUCKET + "/Document Management/" + testClaimId + ".json");

        // Act: Execute orchestration
        orchestrationService.standardizeAndOrchestrate(testClaimId, standardPayload);

        // Assert: Verify execution order using InOrder to guarantee authorization precedes intake
        InOrder inOrder = inOrder(agentAuthorizationService, intakeProcessingService, claimDataStoreClient, documentManagementClient);
        inOrder.verify(agentAuthorizationService).validate("AGENT-VALID-123");
        inOrder.verify(intakeProcessingService).processIntake(testClaimId, standardPayload);
        inOrder.verify(claimDataStoreClient).putItem(CLAIM_DATA_STORE_TABLE, PK_ATTRIBUTE, eq(testClaimId), payloadCaptor.capture());
        inOrder.verify(documentManagementClient).putObject(DOCUMENT_MANAGEMENT_BUCKET, eq("Document Management/" + testClaimId + ".json"), any(byte[].class));

        // Assert: Validate standardized payload state and structured logging
        Map<String, Object> savedPayload = payloadCaptor.getValue();
        assertEquals("INTAKE_COMPLETED", savedPayload.get("status"));
        assertEquals("SYSTEM", savedPayload.get("validatedBy"));
        verify(structuredLogger).info("Claim data standardized and persisted: {}", testClaimId);
    }

    @Test
    void should_fail_on_missing_agent_id_input_validation() {
        // Arrange: Payload violates input validation contract (missing required agentId)
        Map<String, Object> invalidPayload = new HashMap<>(standardPayload);
        invalidPayload.remove("agentId");

        // Act & Assert: Orchestration must reject invalid input before calling downstream services
        assertThrows(IllegalArgumentException.class, () -> 
            orchestrationService.standardizeAndOrchestrate(testClaimId, invalidPayload)
        );
        
        // Verify no downstream I/O was triggered due to early validation failure
        verifyNoInteractions(agentAuthorizationService, intakeProcessingService, claimDataStoreClient, documentManagementClient);
        verify(structuredLogger).error("Input validation failed for claim {}: missing required field", testClaimId);
    }
}
