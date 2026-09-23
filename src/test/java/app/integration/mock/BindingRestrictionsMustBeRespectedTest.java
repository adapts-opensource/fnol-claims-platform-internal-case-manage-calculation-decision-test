package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;

// Infrastructure I/O interfaces matching the provided data model and contracts
interface DynamoDBStore {
    Map<String, Object> putItem(String tableName, Map<String, Object> item);
}

interface S3Storage {
    String writePayload(String bucketName, String objectKey);
}

interface SESCommunications {
    String sendNotification(String fromAddress, List<String> toAddresses, String region);
}

// Core calculation service under test
class StateTransitionCalculator {
    private final DynamoDBStore dynamoDBStore;
    private final S3Storage s3Storage;
    private final SESCommunications sesCommunications;

    StateTransitionCalculator(DynamoDBStore dynamoDBStore, S3Storage s3Storage, SESCommunications sesCommunications) {
        this.dynamoDBStore = dynamoDBStore;
        this.s3Storage = s3Storage;
        this.sesCommunications = sesCommunications;
    }

    public Map<String, Object> calculateStateTransition(String submissionId, Map<String, Object> payload) {
        // NFR: Input Validation
        if (payload == null || !payload.containsKey("id") || !payload.containsKey("binding_restrictions")) {
            throw new IllegalArgumentException("Invalid payload: missing required fields for state transition");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> restrictions = (Map<String, Object>) payload.get("binding_restrictions");
        boolean enforced = Boolean.TRUE.equals(restrictions.get("enforced"));

        // Business Rule: Binding restrictions must be respected
        if (!enforced) {
            throw new IllegalStateException("Binding restrictions must be respected. Transition blocked.");
        }

        Map<String, Object> itemPayload = new HashMap<>(payload);
        itemPayload.put("current_state", "SUBMITTED");
        itemPayload.put("binding_restrictions_respected", true);

        // NFR: Compliance & Observability - Infra I/O contracts
        dynamoDBStore.putItem("Data_Store_table", itemPayload);
        s3Storage.writePayload("Claim_Intake_Service-bucket", submissionId + ".json");
        sesCommunications.sendNotification("fnol@newco.com", List.of("claims@newco.com"), "us-east-1");

        return itemPayload;
    }
}

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolStateTransitionCalculationTest {

    @Mock
    private DynamoDBStore dynamoDBStore;

    @Mock
    private S3Storage s3Storage;

    @Mock
    private SESCommunications sesCommunications;

    @InjectMocks
    private StateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock lifecycle per test method, ensuring thread safety
    }

    @Test
    void binding_restrictions_must_be_respected() {
        // Arrange
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", submissionId);
        payload.put("channel", "WEB");
        payload.put("binding_restrictions", Map.of("enforced", true, "compliance_check", "PASSED"));

        when(dynamoDBStore.putItem(anyString(), any(Map.class))).thenReturn(Map.of("id", submissionId, "status", "SUCCESS"));
        when(s3Storage.writePayload(anyString(), anyString())).thenReturn("s3://Claim_Intake_Service-bucket/" + submissionId + ".json");
        when(sesCommunications.sendNotification(anyString(), anyList(), anyString())).thenReturn("msg-uuid-123");

        // Act
        Map<String, Object> result = calculator.calculateStateTransition(submissionId, payload);

        // Assert
        assertNotNull(result, "State transition result must not be null");
        assertEquals("SUBMITTED", result.get("current_state"), "State must transition to SUBMITTED when restrictions are respected");
        assertTrue((Boolean) result.get("binding_restrictions_respected"), "Binding restrictions must be marked as respected");

        // Verify infrastructure I/O contracts were invoked exactly once
        verify(dynamoDBStore, times(1)).putItem(eq("Data_Store_table"), any(Map.class));
        verify(s3Storage, times(1)).writePayload(eq("Claim_Intake_Service-bucket"), anyString());
        verify(sesCommunications, times(1)).sendNotification(anyString(), anyList(), eq("us-east-1"));

        // Verify no unexpected interactions (least privilege / strict contract adherence)
        verifyNoMoreInteractions(dynamoDBStore, s3Storage, sesCommunications);
    }
}
