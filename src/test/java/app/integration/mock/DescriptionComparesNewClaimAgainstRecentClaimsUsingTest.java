package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Domain models for compilation safety
record RecentClaim(String policyNumber, String riskAddress, LocalDate dateOfLoss,
                   String causeOfLoss, String catastropheEvent, String reporter,
                   String damagedArea, String priorClaimStatus) {}

record DecisionResult(String claimId, double confidenceScore, double threshold, boolean flagForReview) {}

interface ClaimComparisonService {
    List<RecentClaim> fetchRecentClaims(String policyNumber, String riskAddress, LocalDate dateOfLoss,
                                        String causeOfLoss, String catastropheEvent, String reporter,
                                        String damagedArea, String priorClaimStatus);
}

interface ScoringService {
    double calculateCompositeScore(String claimId, List<RecentClaim> recentClaims);
}

interface ThresholdConfigService {
    double getReviewThreshold();
}

interface ReviewNotificationService {
    void notifyReviewFlag(String claimId, double score, double threshold);
}

interface StructuredLogger {
    void logDecision(String claimId, double score, double threshold, boolean flagged);
}

// Subject Under Test
class ClaimDecisionOrchestrator {
    private final ClaimComparisonService comparisonService;
    private final ScoringService scoringService;
    private final ThresholdConfigService thresholdService;
    private final ReviewNotificationService notificationService;
    private final StructuredLogger logger;

    ClaimDecisionOrchestrator(ClaimComparisonService comparisonService, ScoringService scoringService,
                              ThresholdConfigService thresholdService, ReviewNotificationService notificationService,
                              StructuredLogger logger) {
        this.comparisonService = comparisonService;
        this.scoringService = scoringService;
        this.thresholdService = thresholdService;
        this.notificationService = notificationService;
        this.logger = logger;
    }

    DecisionResult processDecision(String claimId, String policyNumber, String riskAddress,
                                   LocalDate dateOfLoss, String causeOfLoss, String catastropheEvent,
                                   String reporter, String damagedArea, String priorClaimStatus) {
        // Input validation (GDPR/SOC2 compliant: reject null/empty PII or critical identifiers)
        if (policyNumber == null || riskAddress == null || dateOfLoss == null) {
            throw new IllegalArgumentException("Policy number, risk address, and date of loss are required.");
        }

        List<RecentClaim> recentClaims = comparisonService.fetchRecentClaims(policyNumber, riskAddress, dateOfLoss,
                causeOfLoss, catastropheEvent, reporter, damagedArea, priorClaimStatus);
        double score = scoringService.calculateCompositeScore(claimId, recentClaims);
        double threshold = thresholdService.getReviewThreshold();
        boolean flag = score > threshold;
        
        // Observability: Structured logging
        logger.logDecision(claimId, score, threshold, flag);
        
        if (flag) {
            notificationService.notifyReviewFlag(claimId, score, threshold);
        }
        return new DecisionResult(claimId, score, threshold, flag);
    }
}

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementOrchestrationDecisionTest {

    @Mock private ClaimComparisonService comparisonService;
    @Mock private ScoringService scoringService;
    @Mock private ThresholdConfigService thresholdService;
    @Mock private ReviewNotificationService notificationService;
    @Mock private StructuredLogger logger;

    private ClaimDecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimDecisionOrchestrator(comparisonService, scoringService, thresholdService,
                notificationService, logger);
    }

    @Test
    void description_compares_new_claim_against_recent_claims_using_policy_number_risk_address_date_of_loss_cause_of_loss_catastrophe_event_reporter_damaged_area_and_prior_claim_status_generates_a_composite_confidence_score_and_thresholds_to_flag_for_review() {
        // Arrange
        String claimId = "CLM-2023-001";
        String policyNumber = "POL-98765";
        String riskAddress = "123 Oak St";
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 15);
        String causeOfLoss = "Fire";
        String catastropheEvent = "Wildfire";
        String reporter = "John Doe";
        String damagedArea = "Kitchen";
        String priorClaimStatus = "Approved";
        List<RecentClaim> recentClaims = List.of(new RecentClaim(policyNumber, riskAddress, dateOfLoss, causeOfLoss, catastropheEvent, reporter, damagedArea, priorClaimStatus));
        double expectedScore = 0.85;
        double threshold = 0.75;

        when(comparisonService.fetchRecentClaims(policyNumber, riskAddress, dateOfLoss, causeOfLoss, catastropheEvent, reporter, damagedArea, priorClaimStatus)).thenReturn(recentClaims);
        when(scoringService.calculateCompositeScore(claimId, recentClaims)).thenReturn(expectedScore);
        when(thresholdService.getReviewThreshold()).thenReturn(threshold);

        // Act
        DecisionResult result = orchestrator.processDecision(claimId, policyNumber, riskAddress, dateOfLoss, causeOfLoss, catastropheEvent, reporter, damagedArea, priorClaimStatus);

        // Assert
        assertEquals(claimId, result.claimId());
        assertEquals(expectedScore, result.confidenceScore(), 0.001);
        assertEquals(threshold, result.threshold(), 0.001);
        assertTrue(result.flagForReview());
        verify(notificationService).notifyReviewFlag(claimId, expectedScore, threshold);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).logDecision(captor.capture(), eq(expectedScore), eq(threshold), eq(true));
        assertEquals(claimId, captor.getValue());
    }

    @Test
    void testThreadSafetyWithConcurrentClaimProcessing() throws InterruptedException {
        // Arrange
        String claimId = "CLM-CONCURRENT-001";
        List<RecentClaim> recentClaims = List.of();
        double score = 0.5;
        double threshold = 0.75;
        
        when(comparisonService.fetchRecentClaims(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(recentClaims);
        when(scoringService.calculateCompositeScore(eq(claimId), anyList())).thenReturn(score);
        when(thresholdService.getReviewThreshold()).thenReturn(threshold);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orchestrator.processDecision(claimId, "POL-1", "Addr-1", LocalDate.now(), "Fire", "None", "User", "Room", "Pending");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Concurrent processing failed", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(5, successCount.get(), "All concurrent threads should complete successfully");
        verify(scoringService, times(5)).calculateCompositeScore(eq(claimId), anyList());
    }

    @Test
    void testInputValidationRejectsNullCriticalFields() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.processDecision("CLM-1", null, "123 St", LocalDate.now(), "Fire", "None", "User", "Room", "Pending");
        });
        assertEquals("Policy number, risk address, and date of loss are required.", exception.getMessage());
    }

    @Test
    void testStructuredLoggingCapturesDecisionContext() {
        // Arrange
        String claimId = "CLM-LOG-001";
        double score = 0.6;
        double threshold = 0.75;
        when(comparisonService.fetchRecentClaims(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(scoringService.calculateCompositeScore(eq(claimId), anyList())).thenReturn(score);
        when(thresholdService.getReviewThreshold()).thenReturn(threshold);

        // Act
        orchestrator.processDecision(claimId, "POL-1", "Addr-1", LocalDate.now(), "Fire", "None", "User", "Room", "Pending");

        // Assert
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger).logDecision(idCaptor.capture(), eq(score), eq(threshold), eq(false));
        assertEquals(claimId, idCaptor.getValue());
        verifyNoInteractions(notificationService);
    }
}
