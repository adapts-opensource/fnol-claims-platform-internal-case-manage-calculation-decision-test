package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatastropheEventWithManySimilarClaimsTest {

    @Mock
    private StateTransitionCalculator calculator;

    @Mock
    private S3Client s3Client;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    private static final String CATASTROPHE_EVENT_ID = "evt-catastrophe-001";
    private static final int CLAIM_BATCH_SIZE = 5;

    @BeforeEach
    void setUp() {
        // Mock initialization and NFR setup (TLS, least_privilege, structured_logging)
    }

    @Test
    void catastrophe_event_with_many_similar_claims() {
        List<Map<String, Object>> claimPayloads = generateSimilarClaimPayloads();

        when(calculator.calculateStateTransitions(anyList(), anyString()))
                .thenReturn(claimPayloads);

        List<Map<String, Object>> results = calculator.calculateStateTransitions(claimPayloads, CATASTROPHE_EVENT_ID);

        assertEquals(CLAIM_BATCH_SIZE, results.size());
        results.forEach(result -> {
            assertTrue(result.containsKey("id"));
            assertTrue(result.containsKey("payload"));
            assertEquals("PENDING_REVIEW", result.get("state"));
        });

        verify(s3Client, times(CLAIM_BATCH_SIZE)).putObject(any(), any());
        verify(dynamoDbClient, times(CLAIM_BATCH_SIZE)).updateItem(any());
        verify(sesClient, times(1)).sendEmail(any());
    }

    private List<Map<String, Object>> generateSimilarClaimPayloads() {
        return List.of(
            createClaimPayload(UUID.randomUUID().toString()),
            createClaimPayload(UUID.randomUUID().toString()),
            createClaimPayload(UUID.randomUUID().toString()),
            createClaimPayload(UUID.randomUUID().toString()),
            createClaimPayload(UUID.randomUUID().toString())
        );
    }

    private Map<String, Object> createClaimPayload(String claimId) {
        return Map.of(
            "id", claimId,
            "event_id", CATASTROPHE_EVENT_ID,
            "claim_type", "AUTO_COLLISION",
            "severity", "LOW",
            "timestamp", System.currentTimeMillis()
        );
    }
}
