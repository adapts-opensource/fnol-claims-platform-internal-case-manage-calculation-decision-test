package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationTransformationOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    @InjectMocks
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // NFR: thread_safety & concurrency - MockitoExtension isolates state per test execution
        // NFR: observability - structured_logging would be verified via captor if logger was injected
    }

    @Test
    void regulatory_flags_valid() {
        // Given
        String claimId = "CLM-REG-001";
        Map<String, Object> inputPayload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "regulatoryFlags", Map.of(
                    "complianceStatus", "VALID",
                    "jurisdiction", "US-CA",
                    "flags", List.of("FLAG_1", "FLAG_2")
                ),
                "claimType", "FNOL",
                "status", "PENDING_STANDARDIZATION"
            )
        );

        // Mock external I/O contracts per infra_io_contracts
        when(claimDataStoreClient.putItem(eq("Claim Data Store_table"), anyMap()))
            .thenReturn(Map.of("Item", inputPayload));
        when(documentManagementClient.putObject(eq("Document Management-bucket"), anyString(), any(byte[].class)))
            .thenReturn("s3://Document Management-bucket/CLM-REG-001.json");

        // When
        Map<String, Object> result = orchestrationService.transformAndOrchestrate(claimId, inputPayload);

        // Then
        assertNotNull(result, "Orchestration should return a result map");
        assertTrue(result.containsKey("payload"), "Result must contain transformed payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPayload = (Map<String, Object>) result.get("payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> regFlags = (Map<String, Object>) resultPayload.get("regulatoryFlags");

        assertEquals("VALID", regFlags.get("complianceStatus"), "Regulatory compliance status must be preserved");
        assertEquals(List.of("FLAG_1", "FLAG_2"), regFlags.get("flags"), "Regulatory flags list must match input");
        assertEquals("US-CA", regFlags.get("jurisdiction"), "Jurisdiction metadata must be intact");

        // Verify infra I/O contracts were invoked exactly once with correct logical names
        verify(claimDataStoreClient, times(1)).putItem(eq("Claim Data Store_table"), anyMap());
        verify(documentManagementClient, times(1)).putObject(eq("Document Management-bucket"), eq("CLM-REG-001.json"), any(byte[].class));
    }
}
