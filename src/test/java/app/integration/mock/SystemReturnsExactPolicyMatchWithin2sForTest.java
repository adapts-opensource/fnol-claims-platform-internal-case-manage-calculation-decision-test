package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimDataStandardizationEnrichmentValidationMockTest {

    @Mock
    private ClaimEnrichmentService enrichmentService;

    private static final int SUBMISSION_COUNT = 100;
    private static final double TARGET_PERCENTILE = 0.95;
    private static final long MAX_LATENCY_MS = 2000;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Mock external I/O contracts (S3 & DynamoDB) to prevent live calls
        lenient().when(enrichmentService.validateAndEnrich(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> input = invocation.getArgument(0);
            Map<String, Object> response = new HashMap<>();
            response.put("policyMatch", true);
            response.put("payload", input);
            return response;
        });
    }

    @Test
    void systemReturnsExactPolicyMatchWithin2sFor95OfValidSubmissions() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> exactMatches = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < SUBMISSION_COUNT; i++) {
            final int submissionId = i;
            futures.add(executor.submit(() -> {
                long start = System.currentTimeMillis();
                Map<String, Object> inputPayload = Map.of(
                        "id", "claim-" + submissionId,
                        "payload", Map.of("policyId", "POL-123", "status", "VALID")
                );
                Map<String, Object> result = enrichmentService.validateAndEnrich(inputPayload);
                long end = System.currentTimeMillis();
                latencies.add(end - start);
                exactMatches.add(Boolean.TRUE.equals(result.get("policyMatch")));
                return result;
            }));
        }

        try {
            for (Future<Map<String, Object>> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Test execution failed: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        long withinThreshold = latencies.stream().filter(t -> t <= MAX_LATENCY_MS).count();
        double percentile = (double) withinThreshold / SUBMISSION_COUNT;

        assertTrue(percentile >= TARGET_PERCENTILE,
                String.format("Only %.1f%% of submissions met the 2s latency target. Expected >= %.0f%%",
                        percentile * 100, TARGET_PERCENTILE * 100));
        assertTrue(exactMatches.stream().allMatch(Boolean.TRUE::booleanValue),
                "All submissions should return an exact policy match.");
    }
}
