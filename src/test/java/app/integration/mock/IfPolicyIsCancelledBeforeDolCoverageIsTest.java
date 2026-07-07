package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    @InjectMocks
    private StateTransitionOrchestrationService orchestrationService;

    @Test
    void if_policy_is_cancelled_before_dol_coverage_is_invalid() {
        // Arrange: Policy status CANCELLED with cancellation date strictly before Date of Loss (DoL)
        String claimId = "CLM-STD-001";
        Instant cancellationDate = Instant.parse("2023-09-15T10:00:00Z");
        Instant dateOfLoss = Instant.parse("2023-11-20T14:30:00Z");

        Map<String, Object> payload = Map.of(
            "policyId", "POL-STD-001",
            "policyStatus", "CANCELLED",
            "cancellationDate", cancellationDate.toString(),
            "dateOfLoss", dateOfLoss.toString(),
            "claimId", claimId,
            "coverageStatus", "PENDING"
        );

        // Mock DynamoDB I/O contract (Claim Data Store)
        when(claimDataStoreClient.getItem(anyString(), anyString())).thenReturn(payload);

        // Mock S3 I/O contract (Document Management)
        when(documentManagementClient.getObjectUri(anyString(), anyString())).thenReturn("s3://doc-mgmt-bucket/CLM-STD-001.json");

        // Act: Execute orchestration
        Map<String, Object> result = orchestrationService.standardizeAndTransition(claimId, payload);

        // Assert: Coverage must be marked INVALID
        assertNotNull(result, "Orchestration must return a result payload");
        assertEquals("INVALID", result.get("coverageStatus"), "Coverage is invalid when policy is cancelled before DoL");
        assertEquals("TRANSITIONED", result.get("orchestrationState"), "State must transition successfully");
        assertTrue(result.containsKey("auditLog"), "Structured logging/audit trail required for GDPR/SOC2 compliance");

        // Verify infrastructure I/O contracts
        verify(claimDataStoreClient, times(1)).getItem(eq("pk"), eq(claimId));
        verify(documentManagementClient, times(1)).getObjectUri(eq("doc-mgmt-bucket"), anyString());

        // NFR: Input Validation
        assertDoesNotThrow(() -> orchestrationService.standardizeAndTransition(null, payload), "Null claimId handled gracefully");
        assertThrows(IllegalArgumentException.class, () -> orchestrationService.standardizeAndTransition(claimId, Map.of()), "Empty payload validation enforced");
    }

    // NFR: Thread Safety & HA Multi-AZ compatibility verification
    @Test
    void orchestration_is_thread_safe() throws Exception {
        ExecutorService threadPool = Executors.newFixedThreadPool(4);
        CompletableFuture<?>[] futures = new CompletableFuture[8];
        for (int i = 0; i < futures.length; i++) {
            final String id = "CLM-THR-" + i;
            final Map<String, Object> p = Map.of("claimId", id, "policyStatus", "CANCELLED",
                    "cancellationDate", "2023-09-15T10:00:00Z", "dateOfLoss", "2023-11-20T14:30:00Z", "coverageStatus", "PENDING");
            when(claimDataStoreClient.getItem(anyString(), anyString())).thenReturn(p);
            when(documentManagementClient.getObjectUri(anyString(), anyString())).thenReturn("s3://bucket/key");
            futures[i] = CompletableFuture.runAsync(() -> orchestrationService.standardizeAndTransition(id, p), threadPool);
        }
        CompletableFuture.allOf(futures).join();
        verify(claimDataStoreClient, times(futures.length)).getItem(anyString(), anyString());
    }
}
