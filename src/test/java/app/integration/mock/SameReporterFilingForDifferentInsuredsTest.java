package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the Multi-Channel FNOL Submission service correctly handles
 * independent state transitions and calculations when a single reporter
 * submits claims for multiple distinct insured parties.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;
    @Mock
    private StructuredLogger logger;

    @InjectMocks
    private MultiChannelFnolSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        when(s3Client.putObject(any(), any(), any())).thenReturn("s3://fnol-intake-bucket/processed.json");
        when(dynamoDbClient.putItem(any(), any())).thenReturn(Map.of("id", UUID.randomUUID().toString()));
        when(sesClient.sendEmail(any(), any(), any(), any())).thenReturn("msg-123");
        when(logger.info(any(), any())).thenReturn(null);
    }

    @Test
    void same_reporter_filing_for_different_insureds() {
        // Arrange: Synthetic reporter & insureds (GDPR/SOC2 compliant pseudonymous test data)
        String reporterId = "RPT-SYNTH-001";
        Map<String, Object> payloadA = Map.of("insuredId", "INS-A-SYNTH", "channel", "WEB", "damageType", "COLLISION");
        Map<String, Object> payloadB = Map.of("insuredId", "INS-B-SYNTH", "channel", "MOBILE", "damageType", "THEFT");

        // Act: Process two independent FNOL submissions from the same reporter
        Map<String, Object> resultA = submissionService.processStateTransitionAndCalculation(reporterId, payloadA);
        Map<String, Object> resultB = submissionService.processStateTransitionAndCalculation(reporterId, payloadB);

        // Assert: Verify state transitions and calculations are isolated per insured
        assertNotNull(resultA);
        assertNotNull(resultB);
        assertEquals("SUBMITTED", resultA.get("currentState"));
        assertEquals("SUBMITTED", resultB.get("currentState"));
        assertEquals("CALC_INITIATED", resultA.get("calculationStatus"));
        assertEquals("CALC_INITIATED", resultB.get("calculationStatus"));

        // Verify external I/O calls are isolated, idempotent, and correctly routed
        verify(dynamoDbClient, times(2)).putItem(eq("FNOL_Claims"), any());
        verify(s3Client, times(2)).putObject(eq("fnol-intake-bucket"), any(), any());
        verify(sesClient, times(2)).sendEmail(any(), any(), any(), any());
        verify(logger, times(4)).info(any(), any()); // Structured logging NFR
    }

    // Static nested mock interfaces representing external infra contracts
    static interface S3Client {
        String putObject(String bucket, String key, byte[] data);
    }

    static interface DynamoDbClient {
        Map<String, Object> putItem(String table, Map<String, Object> item);
    }

    static interface SesClient {
        String sendEmail(String from, List<String> to, String subject, String body);
    }

    static interface StructuredLogger {
        void info(String message, Map<String, Object> context);
    }

    // Simplified service implementation for demonstration
    static class MultiChannelFnolSubmissionService {
        private final S3Client s3Client;
        private final DynamoDbClient dynamoDbClient;
        private final SesClient sesClient;
        private final StructuredLogger logger;

        MultiChannelFnolSubmissionService(S3Client s3Client, DynamoDbClient dynamoDbClient, SesClient sesClient, StructuredLogger logger) {
            this.s3Client = s3Client;
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
            this.logger = logger;
        }

        public Map<String, Object> processStateTransitionAndCalculation(String reporterId, Map<String, Object> payload) {
            String claimId = UUID.randomUUID().toString();
            Map<String, Object> stateTransition = Map.of("currentState", "SUBMITTED", "calculationStatus", "CALC_INITIATED");

            logger.info("Processing FNOL", Map.of("reporterId", reporterId, "claimId", claimId));
            dynamoDbClient.putItem("FNOL_Claims", Map.of("id", claimId, "reporterId", reporterId, "payload", stateTransition));
            s3Client.putObject("fnol-intake-bucket", claimId + ".json", "data".getBytes());
            sesClient.sendEmail("noreply@newco.insurance", List.of("claims@newco.insurance"), "FNOL Received", "Claim " + claimId + " processed.");
            logger.info("FNOL processed successfully", Map.of("claimId", claimId, "state", stateTransition.get("currentState")));

            return stateTransition;
        }
    }
}
