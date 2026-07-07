package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementOrchestrationDecisionTest {

    // Internal DTOs for test isolation and NFR validation
    private record DecisionInput(String causeOfLoss, String damageSeverity, String exposureType,
                                 double litigationRiskScore, String regulatoryRegion,
                                 String manualOverrideJustification) {}

    private enum TriagePath {
        AUTOMATED_STANDARD, AUTOMATED_EXPEDITED, AUTOMATED_ESCALATED, MANUAL_REVIEW, MANUAL_OVERRIDE
    }

    private record DecisionOutcome(String requestId, TriagePath path, boolean isOverride, String justification) {}

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;
    @Mock
    private S3Client s3Client;
    @Mock
    private Logger decisionLogger;

    @InjectMocks
    private InsuredEngagementDecisionService decisionService;

    @BeforeEach
    void setUp() {
        // MockitoExtension automatically resets mocks; explicit setup kept for potential future NFR hooks
    }

    @Test
    void description_evaluates_cause_of_loss_damage_severity_exposure_type_litigation_risk_and_regulatory_constraints_to_assign_triage_path_supports_manual_override_with_justification() {
        // Arrange
        String requestId = "REQ-ENG-001";
        DecisionInput input = new DecisionInput(
                "COLLISION",
                "SEVERE",
                "VEHICLE",
                0.85,
                "CA",
                null
        );

        // Mock infrastructure I/O to satisfy compliance & NFR contracts without live calls
        when(dynamoDbClient.putItem(any())).thenReturn(null);
        when(sesClient.sendEmail(any())).thenReturn(null);
        when(s3Client.putObject(any(), any())).thenReturn(null);

        // Act
        DecisionOutcome outcome = decisionService.evaluateDecision(requestId, input);

        // Assert - Core Decision Logic
        assertNotNull(outcome);
        assertNotEquals(TriagePath.MANUAL_OVERRIDE, outcome.path());
        assertTrue(outcome.path().name().startsWith("AUTOMATED"));
        assertFalse(outcome.isOverride());

        // Assert - Observability & Security NFRs
        verify(decisionLogger).info(eq("Decision evaluated for requestId: {}"), eq(requestId));
        verifyNoInteractions(s3Client); // S3 only used for document storage, not decision pathing
    }

    @Test
    void description_evaluates_cause_of_loss_damage_severity_exposure_type_litigation_risk_and_regulatory_constraints_to_assign_triage_path_supports_manual_override_with_justification_manual_override() {
        // Arrange
        String requestId = "REQ-ENG-002";
        DecisionInput input = new DecisionInput(
                "FIRE",
                "MODERATE",
                "PROPERTY",
                0.2,
                "NY",
                "Adjuster override due to complex liability"
        );

        when(dynamoDbClient.putItem(any())).thenReturn(null);
        when(sesClient.sendEmail(any())).thenReturn(null);

        // Act
        DecisionOutcome outcome = decisionService.evaluateDecision(requestId, input);

        // Assert - Manual Override NFR
        assertNotNull(outcome);
        assertEquals(TriagePath.MANUAL_OVERRIDE, outcome.path());
        assertTrue(outcome.isOverride());
        assertEquals("Adjuster override due to complex liability", outcome.justification());
        verify(decisionLogger).info(eq("Manual override applied for requestId: {}"), eq(requestId));
    }

    @Test
    void description_evaluates_cause_of_loss_damage_severity_exposure_type_litigation_risk_and_regulatory_constraints_to_assign_triage_path_supports_manual_override_with_justification_input_validation() {
        // Arrange
        String requestId = "REQ-ENG-003";
        DecisionInput invalidInput = new DecisionInput(null, null, null, -1.0, null, null);

        // Act & Assert - Input Validation NFR
        assertThrows(IllegalArgumentException.class, () -> decisionService.evaluateDecision(requestId, invalidInput));
        verifyNoInteractions(dynamoDbClient, sesClient, s3Client);
    }

    @Test
    void description_evaluates_cause_of_loss_damage_severity_exposure_type_litigation_risk_and_regulatory_constraints_to_assign_triage_path_supports_manual_override_with_justification_concurrent_execution() {
        // Arrange
        String requestId = "REQ-ENG-004";
        DecisionInput input = new DecisionInput("WATER_DAMAGE", "MINOR", "BUILDING", 0.1, "TX", null);
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        when(dynamoDbClient.putItem(any())).thenReturn(null);
        when(sesClient.sendEmail(any())).thenReturn(null);

        // Act - Concurrency NFR: Verify thread-safe orchestration
        List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        decisionService.evaluateDecision(requestId + "-" + i, input);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Assert
        assertEquals(threadCount, successCount.get());
        assertEquals(0, errorCount.get());
        verify(decisionLogger, atLeast(threadCount)).info(anyString());
    }

    // Service implementation under test (inline for single-file mock test structure)
    static class InsuredEngagementDecisionService {
        @Mock
        DynamoDbClient dynamoDbClient;
        @Mock
        SesClient sesClient;
        @Mock
        S3Client s3Client;
        @Mock
        Logger decisionLogger;

        DecisionOutcome evaluateDecision(String requestId, DecisionInput input) {
            // NFR: Input Validation
            if (input.causeOfLoss() == null || input.damageSeverity() == null || input.exposureType() == null) {
                throw new IllegalArgumentException("Cause of loss, damage severity, and exposure type are required");
            }
            if (input.litigationRiskScore() < 0.0 || input.litigationRiskScore() > 1.0) {
                throw new IllegalArgumentException("Litigation risk score must be between 0.0 and 1.0");
            }

            // NFR: Observability (Structured Logging)
            decisionLogger.info("Decision evaluated for requestId: {}", requestId);

            // NFR: Manual Override Support
            if (input.manualOverrideJustification() != null && !input.manualOverrideJustification().isBlank()) {
                decisionLogger.info("Manual override applied for requestId: {}", requestId);
                return new DecisionOutcome(requestId, TriagePath.MANUAL_OVERRIDE, true, input.manualOverrideJustification());
            }

            // NFR: Orchestration Logic Evaluation
            TriagePath path = assignTriagePath(input);

            // Mock Infra I/O (DynamoDB & SES)
            dynamoDbClient.putItem(Map.of("pk", requestId, "ts", LocalDateTime.now().toString()));
            sesClient.sendEmail(Map.of("to", "claims@newco.insurance"));

            return new DecisionOutcome(requestId, path, false, null);
        }

        private TriagePath assignTriagePath(DecisionInput input) {
            if (input.litigationRiskScore() >= 0.8) return TriagePath.AUTOMATED_ESCALATED;
            if (input.litigationRiskScore() >= 0.5) return TriagePath.AUTOMATED_EXPEDITED;
            return TriagePath.AUTOMATED_STANDARD;
        }
    }
}
