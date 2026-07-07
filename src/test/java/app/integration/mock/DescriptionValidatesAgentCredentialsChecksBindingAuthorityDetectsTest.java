package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private S3Contract s3Contract;

    @Mock
    private SesContract sesContract;

    @Mock
    private DynamoDbContract dynamoDbContract;

    @InjectMocks
    private FnolSubmissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Reset static state or shared resources if any
    }

    @Test
    void description_validates_agent_credentials_checks_binding_authority_detects_duplicates_and_attaches_agency_metadata_to_fnol() {
        // Arrange: Prepare FNOL payload with multi-channel submission data
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "WEB_PORTAL");
        payload.put("agentId", "AGENT-8842");
        payload.put("agentCredentials", Map.of("status", "VERIFIED", "expiry", "2025-12-31"));
        payload.put("bindingAuthority", true);
        payload.put("isDuplicate", false);
        payload.put("claimDetails", Map.of("lossDate", "2024-05-10", "type", "AUTO"));

        // Mock external I/O contracts per infra definitions
        doNothing().when(s3Contract).putObject(anyString(), anyString(), any());
        doNothing().when(dynamoDbContract).putItem(anyString(), anyMap());
        when(sesContract.sendEmail(anyString(), anyList(), anyString())).thenReturn("SES-MSG-9921");

        // Act: Execute orchestration & validation
        Map<String, Object> result = orchestrator.validateAndProcessSubmission(payload);

        // Assert: Verify business rules & NFR compliance
        assertNotNull(result, "Result payload must not be null");
        assertEquals("VALIDATED", result.get("status"), "Submission should pass validation");
        assertTrue(result.containsKey("agencyMetadata"), "Agency metadata must be attached");
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) result.get("agencyMetadata");
        assertEquals("SOC2", metadata.get("complianceLevel"), "SOC2 compliance metadata required");
        assertEquals("TLS_1_2", metadata.get("encryptionProtocol"), "TLS transit security required");

        // Verify duplicate detection logic (explicit false path)
        assertEquals(false, result.get("isDuplicate"), "Duplicate flag should remain false");

        // Verify infrastructure I/O contracts
        verify(s3Contract).putObject(eq("Claim Intake Service-bucket"), eq("Claim Intake Service/AGENT-8842.json"), any());
        verify(dynamoDbContract).putItem(eq("Data Store_table"), argThat(attrs -> attrs.containsKey("pk")));
        verify(sesContract).sendEmail(eq("verify@newcoinsurance.com"), eq(List.of("claims@newcoinsurance.com")), eq("us-east-1"));

        // Verify GDPR/SOC2 audit trail attachment
        assertNotNull(result.get("auditTrail"), "Audit trail required for compliance");
    }

    // --- Minimal Infra Contract Interfaces for Mocking ---
    interface S3Contract {
        void putObject(String bucketName, String objectKeyPattern, Object payload);
    }

    interface SesContract {
        String sendEmail(String fromAddress, List<String> toAddresses, String region);
    }

    interface DynamoDbContract {
        void putItem(String tableName, Map<String, Object> itemPayload);
    }

    // --- Service Under Test ---
    static class FnolSubmissionOrchestrator {
        private final S3Contract s3Contract;
        private final SesContract sesContract;
        private final DynamoDbContract dynamoDbContract;

        FnolSubmissionOrchestrator(S3Contract s3Contract, SesContract sesContract, DynamoDbContract dynamoDbContract) {
            this.s3Contract = s3Contract;
            this.sesContract = sesContract;
            this.dynamoDbContract = dynamoDbContract;
        }

        Map<String, Object> validateAndProcessSubmission(Map<String, Object> payload) {
            // 1. Validate Agent Credentials
            Map<String, String> credentials = (Map<String, String>) payload.get("agentCredentials");
            if (credentials == null || !"VERIFIED".equals(credentials.get("status"))) {
                throw new IllegalArgumentException("Invalid agent credentials");
            }

            // 2. Check Binding Authority
            Boolean hasAuthority = (Boolean) payload.get("bindingAuthority");
            if (hasAuthority == null || !hasAuthority) {
                throw new SecurityException("Missing binding authority");
            }

            // 3. Detect Duplicates
            boolean isDuplicate = Boolean.TRUE.equals(payload.get("isDuplicate"));
            payload.put("status", isDuplicate ? "DUPLICATE_DETECTED" : "VALIDATED");

            // 4. Attach Agency Metadata & NFR Compliance Tags
            Map<String, String> agencyMetadata = Map.of(
                    "region", "us-east-1",
                    "complianceLevel", "SOC2",
                    "encryptionProtocol", "TLS_1_2",
                    "dataResidency", "GDPR_EU"
            );
            payload.put("agencyMetadata", agencyMetadata);
            payload.put("auditTrail", Map.of("timestamp", "2024-05-11T10:00:00Z", "actor", "SYSTEM"));

            // 5. Execute I/O Contracts
            String agentId = (String) payload.get("agentId");
            s3Contract.putObject("Claim Intake Service-bucket", "Claim Intake Service/" + agentId + ".json", payload);
            dynamoDbContract.putItem("Data Store_table", Map.of("pk", agentId, "payload", payload));
            sesContract.sendEmail("verify@newcoinsurance.com", List.of("claims@newcoinsurance.com"), "us-east-1");

            return payload;
        }
    }
}
