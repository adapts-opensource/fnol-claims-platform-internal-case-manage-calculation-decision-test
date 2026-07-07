package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolStateTransitionCalculationTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private SesClient sesClient;
    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private FnolStateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        // MockitoExtension resets mocks before each test by default
        // Additional test-specific setup can be added here
    }

    @Test
    void decision_high_duplicate_score_rule_score_0_85_n_expected_outcome_create_task_review_potential_duplicate_claim() {
        // Arrange: Setup payload with duplicate_score > 0.85 per rule definition
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "fnol-001");
        payload.put("duplicate_score", 0.86);
        payload.put("status", "NEW");
        payload.put("channel", "web");

        // Mock S3 input/output contract: reads payload, writes to bucket, returns object_uri
        when(s3Client.getObject(any())).thenReturn(mock(java.io.InputStream.class));
        when(s3Client.putObject(any(), any())).thenReturn("s3://Claim Intake Service-bucket/fnol-001.json");

        // Mock DynamoDB input/output contract: reads/writes item using partition_key
        when(dynamoDbClient.getItem(any())).thenReturn(Map.of("id", "fnol-001", "payload", payload));
        doNothing().when(dynamoDbClient).updateItem(any(), any());

        // Act: Trigger state transition calculation
        StateTransitionResult result = calculator.calculateTransition(payload);

        // Assert: Verify decision matches rule, task is created, and name matches expected outcome
        assertNotNull(result);
        assertEquals("HIGH_DUPLICATE_SCORE", result.getDecision());
        assertTrue(result.isTaskCreated());
        assertEquals("Review Potential Duplicate Claim", result.getTaskName());

        // Verify infra I/O contracts were invoked correctly
        verify(s3Client).getObject(any());
        verify(dynamoDbClient).getItem(any());
        verify(dynamoDbClient).updateItem(any(), any());
    }
}

// Minimal domain and infrastructure interfaces for test compilation context
interface S3Client {
    java.io.InputStream getObject(Object request);
    String putObject(Object request, Object data);
}

interface SesClient {
    String sendEmail(Object request);
}

interface DynamoDbClient {
    Map<String, Object> getItem(Object request);
    void updateItem(Object request, Object data);
}

interface FnolStateTransitionCalculator {
    StateTransitionResult calculateTransition(Map<String, Object> payload);
}

class StateTransitionResult {
    private final String decision;
    private final boolean taskCreated;
    private final String taskName;

    public StateTransitionResult(String decision, boolean taskCreated, String taskName) {
        this.decision = decision;
        this.taskCreated = taskCreated;
        this.taskName = taskName;
    }

    public String getDecision() { return decision; }
    public boolean isTaskCreated() { return taskCreated; }
    public String getTaskName() { return taskName; }
}
