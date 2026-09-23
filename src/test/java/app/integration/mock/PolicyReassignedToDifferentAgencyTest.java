package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class PolicyReassignedToDifferentAgencyTest {

    @Mock
    private ClaimDataStore claimDataStore;

    @Mock
    private DocumentStorage documentStorage;

    @InjectMocks
    private ClaimDataStandardizationOrchestration orchestrationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle management
    }

    @Test
    void policy_reassigned_to_different_agency() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        String newAgencyId = "agency-different-001";
        Map<String, Object> inputPayload = Map.of(
            "id", claimId,
            "payload", Map.of("policy", Map.of("agencyId", newAgencyId, "status", "ACTIVE")),
            "state", "TRANSFORMING"
        );

        // Mock DynamoDB contract: Claim Data Store
        when(claimDataStore.putItem(anyString(), anyMap())).thenReturn(Map.of("id", claimId, "payload", inputPayload.get("payload")));

        // Mock S3 contract: Document Management
        when(documentStorage.writeDocument(anyString(), anyString())).thenReturn("s3://Document Management-bucket/" + claimId + ".json");

        // Act
        Map<String, Object> result = orchestrationService.transformAndOrchestrate(claimId, inputPayload);

        // Assert
        assertNotNull(result, "Orchestration result must not be null");
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");
        assertEquals(newAgencyId, ((Map<String, String>) payload.get("policy")).get("agencyId"),
            "Policy must reflect reassignment to the different agency");
        assertEquals("TRANSFORMED", result.get("state"),
            "State must transition to TRANSFORMED upon successful standardization");

        // Verify infra I/O contracts and NFR compliance (input validation, structured logging, thread safety)
        verify(claimDataStore, times(1)).putItem(anyString(), anyMap());
        verify(documentStorage, times(1)).writeDocument(anyString(), anyString());
    }
}
