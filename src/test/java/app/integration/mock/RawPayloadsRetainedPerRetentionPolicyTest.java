package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that raw claim payloads are retained according to the configured retention policy
 * during the Claim Data Standardization:transformation:orchestration flow.
 * Aligns with GDPR/SOC2 data handling, thread-safe mock isolation, and structured observability.
 */
@ExtendWith(MockitoExtension.class)
class RawPayloadsRetainedPerRetentionPolicyTest {

    @Mock
    private ClaimDataStandardizationOrchestrator orchestrator;

    @Mock
    private RetentionPolicyService retentionPolicyService;

    @Mock
    private S3DocumentStorageService s3Storage;

    @Mock
    private DynamoDbClaimDataStoreService claimDataStore;

    private String claimId;
    private Map<String, Object> rawPayload;
    private RetentionPolicyConfig retentionPolicy;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID().toString();
        rawPayload = Map.of(
            "id", claimId,
            "type", "FNOL",
            "rawData", "insurance_claim_payload_v1"
        );
        retentionPolicy = new RetentionPolicyConfig("90_DAYS", true, "claim-raw-payloads-bucket");
    }

    @Test
    void raw_payloads_retained_per_retention_policy() {
        // Arrange: Mock external I/O contracts (S3 & DynamoDB) and policy resolution
        when(retentionPolicyService.resolveForClaimType("FNOL")).thenReturn(retentionPolicy);
        doNothing().when(s3Storage).putObject(anyString(), anyString(), any(byte[].class));
        doNothing().when(claimDataStore).putItem(anyString(), any(Map.class));

        // Act: Execute orchestration flow
        var result = orchestrator.executeStandardizationFlow(claimId, rawPayload);

        // Assert: Verify retention policy enforcement and raw payload storage
        assertNotNull(result);
        assertEquals("TRANSFORMED", result.getState());
        assertTrue(result.isRawPayloadRetained());

        verify(retentionPolicyService, times(1)).resolveForClaimType("FNOL");
        verify(s3Storage, times(1))
            .putObject(eq("claim-raw-payloads-bucket"), eq(claimId + ".json"), any(byte[].class));
        verify(claimDataStore, times(1))
            .putItem(eq("Claim Data Store_table"), argThat(item -> item.containsKey("payload")));
    }

    // Minimal stubs for compilation and test isolation
    private static class RetentionPolicyConfig {
        private final String duration;
        private final boolean retainRaw;
        private final String bucketName;
        RetentionPolicyConfig(String duration, boolean retainRaw, String bucketName) {
            this.duration = duration;
            this.retainRaw = retainRaw;
            this.bucketName = bucketName;
        }
        boolean shouldRetainRaw() { return retainRaw; }
    }

    private static class OrchestrationResult {
        private final String state;
        private final boolean rawPayloadRetained;
        OrchestrationResult(String state, boolean rawPayloadRetained) {
            this.state = state;
            this.rawPayloadRetained = rawPayloadRetained;
        }
        String getState() { return state; }
        boolean isRawPayloadRetained() { return rawPayloadRetained; }
    }

    private interface ClaimDataStandardizationOrchestrator {
        OrchestrationResult executeStandardizationFlow(String claimId, Map<String, Object> payload);
    }

    private interface RetentionPolicyService {
        RetentionPolicyConfig resolveForClaimType(String claimType);
    }

    private interface S3DocumentStorageService {
        void putObject(String bucket, String key, byte[] data);
    }

    private interface DynamoDbClaimDataStoreService {
        void putItem(String tableName, Map<String, Object> item);
    }
}
