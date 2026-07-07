package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.MDC;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DateRangeStateTransitionTest {

    @Mock private DynamoDBClient dynamoDBClient;
    @Mock private S3Client s3Client;
    @Mock private SesClient sesClient;
    @Mock private Logger logger;
    @Mock private SecretsManagerClient secretsManager;
    @Mock private TlsConfigClient tlsConfig;
    @InjectMocks private StateTransitionService stateTransitionService;

    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        startDate = LocalDate.of(2024, 1, 1);
        endDate = LocalDate.of(2024, 1, 31);
        reset(dynamoDBClient, s3Client, sesClient, logger, secretsManager, tlsConfig);
        MDC.put("feature", "Insured Engagement & Tracking");
        MDC.put("decision", "state_transition");
        MDC.put("trace_id", "mock-trace-uuid");
    }

    @Test
    void date_range() {
        // Arrange: Input validation & Secure config mock
        assertTrue(!startDate.isAfter(endDate), "Start date must not be after end date");
        when(secretsManager.getSecret(anyString())).thenReturn("mock-secure-token");
        when(tlsConfig.isTlsEnabled()).thenReturn(true);

        // Arrange: Mock DynamoDB date range query
        List<StateTransitionRecord> expectedRecords = List.of(
            new StateTransitionRecord("claim-001", LocalDateTime.of(2024, 1, 15, 10, 0), "PENDING"),
            new StateTransitionRecord("claim-002", LocalDateTime.of(2024, 1, 25, 14, 30), "APPROVED")
        );
        when(dynamoDBClient.queryByDateRange(anyString(), anyString(), anyString(), eq(startDate), eq(endDate)))
            .thenReturn(expectedRecords);

        // Arrange: Mock S3 audit & SES notification
        when(s3Client.putObject(anyString(), anyString(), any())).thenReturn("s3://audit-bucket/2024/01/01/transition.log");
        when(sesClient.sendEmail(anyString(), anyList(), anyString())).thenReturn("msg-uuid-123");

        // Act
        List<StateTransitionRecord> result = stateTransitionService.fetchTransitionsInRange(startDate, endDate);

        // Assert: Core date range logic
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> !r.transitionDate().isBefore(startDate) && !r.transitionDate().isAfter(endDate)));

        // Assert: NFR compliance & mock verification
        verify(dynamoDBClient, times(1)).queryByDateRange(anyString(), anyString(), anyString(), eq(startDate), eq(endDate));
        verify(s3Client, times(1)).putObject(anyString(), anyString(), any());
        verify(sesClient, times(1)).sendEmail(anyString(), anyList(), anyString());
        verify(logger, times(1)).info(eq("Processed {} transitions for date range"), eq(2));
        verify(secretsManager, times(1)).getSecret(anyString());
        verify(tlsConfig, times(1)).isTlsEnabled();
        MDC.clear();
    }

    private record StateTransitionRecord(String claimId, LocalDateTime transitionDate, String status) {}
    private interface DynamoDBClient { List<StateTransitionRecord> queryByDateRange(String table, String pk, String sk, LocalDate start, LocalDate end); }
    private interface S3Client { String putObject(String bucket, String key, Object payload); }
    private interface SesClient { String sendEmail(String from, List<String> to, String region); }
    private interface SecretsManagerClient { String getSecret(String key); }
    private interface TlsConfigClient { boolean isTlsEnabled(); }
    private interface StateTransitionService { List<StateTransitionRecord> fetchTransitionsInRange(LocalDate start, LocalDate end); }
}
