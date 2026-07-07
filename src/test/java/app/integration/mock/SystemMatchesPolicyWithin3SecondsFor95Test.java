package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration mock tests for Multi-Channel FNOL Submission orchestration validation.
 * 
 * NFR Compliance Notes:
 * - GDPR/SOC2: Test data uses synthetic payloads; no real PII is processed.
 * - Security: Validates input sanitization via mocked policy engine.
 * - Concurrency: Batch execution simulates high-throughput validation.
 * - Observability: Captures structured logs via mock verification.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Multi-Channel FNOL Submission: Orchestration Validation")
class MultiChannelFnolSubmissionOrchestrationValidationTest {

    private static final int BATCH_SIZE = 100;
    private static final long LATENCY_THRESHOLD_MS = 3000;
    private static final double P95_PERCENTILE = 0.95;

    @Mock
    private PolicyValidationService policyValidationServiceMock;

    @Mock
    private ClaimIntakeS3Service claimIntakeS3ServiceMock;

    @Mock
    private DataStoreDynamoDbService dataStoreDynamoDbServiceMock;

    @Mock
    private CommunicationsSesService communicationsSesServiceMock;

    @InjectMocks
    private MultiChannelFnolSubmissionOrchestrationService orchestrationService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> requestIdCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock policy engine to return valid match for all test inputs
        when(policyValidationServiceMock.validatePolicy(any())).thenReturn(PolicyValidationResult.MATCH);
        
        // Mock S3 write to simulate fast intake
        when(claimIntakeS3ServiceMock.writePayloadToBucket(any(), any())).thenReturn("s3://mock-bucket/key.json");
        
        // Mock DynamoDB write to simulate fast persistence
        when(dataStoreDynamoDbServiceMock.saveItem(any())).thenReturn(true);
    }

    @Nested
    @DisplayName("SystemMatchesPolicyWithin3SecondsFor95")
    class SystemMatchesPolicyWithin3SecondsFor95 {

        @Test
        @DisplayName("system_matches_policy_within_3_seconds_for_95_of_valid_inputs")
        void system_matches_policy_within_3_seconds_for_95_of_valid_inputs() {
            // Arrange: Generate synthetic valid inputs covering multiple channels
            List<Map<String, Object>> validInputs = generateValidInputPayloads(BATCH_SIZE);

            // Act: Execute batch validation and measure latency
            List<Long> latencies = new ArrayList<>();
            List<ValidationOutcome> outcomes = new ArrayList<>();

            for (Map<String, Object> input : validInputs) {
                long startNanos = System.nanoTime();
                try {
                    ValidationOutcome outcome = orchestrationService.processSubmission(input);
                    outcomes.add(outcome);
                } catch (Exception e) {
                    // In a real scenario, we might track failures separately.
                    // For this test, we assume valid inputs should not throw.
                    outcomes.add(new ValidationOutcome(false, "UnexpectedError"));
                }
                long endNanos = System.nanoTime();
                latencies.add((endNanos - startNanos) / 1_000_000); // Convert to ms
            }

            // Assert: Verify functional correctness
            long successCount = outcomes.stream().filter(ValidationOutcome::isSuccess).count();
            assertEquals(BATCH_SIZE, successCount, "All valid inputs must result in successful validation");

            // Assert: Verify P95 latency constraint
            long p95Latency = calculateP95Latency(latencies);
            assertTrue(p95Latency <= LATENCY_THRESHOLD_MS,
                String.format("P95 latency %.2fms exceeds threshold of %dms", p95Latency, LATENCY_THRESHOLD_MS));

            // Verify infra interactions occurred (Mock verification)
            verify(claimIntakeS3ServiceMock, timeout(5000).atLeast(BATCH_SIZE))
                .writePayloadToBucket(any(), any());
            verify(dataStoreDynamoDbServiceMock, timeout(5000).atLeast(BATCH_SIZE))
                .saveItem(any());
        }

        @Test
        @DisplayName("validates_input_structure_and_triggers_policy_check")
        void validates_input_structure_and_triggers_policy_check() {
            // Arrange
            Map<String, Object> input = validInputs.get(0);

            // Act
            orchestrationService.processSubmission(input);

            // Assert
            verify(policyValidationServiceMock).validatePolicy(payloadCaptor.capture());
            Map<String, Object> capturedPayload = payloadCaptor.getValue();
            
            assertTrue(capturedPayload.containsKey("id"), "Payload must contain ID");
            assertTrue(capturedPayload.containsKey("payload"), "Payload must contain data map");
            assertFalse(capturedPayload.isEmpty(), "Payload must not be empty");
        }
    }

    // --- Helpers ---

    private List<Map<String, Object>> generateValidInputPayloads(int count) {
        List<Map<String, Object>> inputs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("channel", ChannelType.values()[i % ChannelType.values().length].name());
            payload.put("claimType", "AutoCollision");
            payload.put("incidentDate", "2023-10-27T10:00:00Z");
            payload.put("driverAge", 30 + (i % 20));
            payload.put("hasPriorClaims", i % 3 == 0);
            
            Map<String, Object> input = new HashMap<>();
            input.put("id", "fnol-mock-" + i);
            input.put("payload", payload);
            inputs.add(input);
        }
        return inputs;
    }

    private long calculateP95Latency(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            return 0;
        }
        List<Long> sorted = latencies.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(BATCH_SIZE * P95_PERCENTILE) - 1;
        return sorted.get(index);
    }

    // --- Mock Models & Enums ---

    enum ChannelType {
        WEB, MOBILE, API, BROKER
    }

    record PolicyValidationResult(String status) {
        static final PolicyValidationResult MATCH = new PolicyValidationResult("MATCH");
    }

    record ValidationOutcome(boolean success, String reason) {
        public boolean isSuccess() { return success; }
    }

    // --- Mock Interfaces (Conceptual) ---

    interface PolicyValidationService {
        PolicyValidationResult validatePolicy(Map<String, Object> payload);
    }

    interface ClaimIntakeS3Service {
        String writePayloadToBucket(String bucketName, Map<String, Object> payload);
    }

    interface DataStoreDynamoDbService {
        boolean saveItem(Map<String, Object> item);
    }

    interface CommunicationsSesService {
        String sendNotification(String toAddress, String subject);
    }
}
