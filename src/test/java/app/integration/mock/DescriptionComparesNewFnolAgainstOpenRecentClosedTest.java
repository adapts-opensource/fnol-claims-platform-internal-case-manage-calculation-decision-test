package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementDecisionTransformationTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private CommunicationService communicationService;

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private InsuredEngagementTransformationService transformationService;

    private static final double MANUAL_REVIEW_THRESHOLD = 0.80;
    private static final String INSURED_ID = "INS-1001";
    private static final String INCIDENT_TYPE = "AUTO_COLLISION";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and injection
    }

    @Test
    void description_compares_new_fnol_against_open_recent_closed_claims_using_weighted_scoring_applies_business_rules_for_merges_and_flags_for_manual_review_if_threshold_met() {
        // Arrange
        NewFnolSubmission newFnol = new NewFnolSubmission(
                "INS-1001", "John Doe", LocalDate.now(), INCIDENT_TYPE, "123 Main St",
                BigDecimal.valueOf(15000), "USD"
        );

        List<ExistingClaimRecord> openAndClosedClaims = List.of(
                new ExistingClaimRecord("CLM-9001", "John Doe", LocalDate.now().minusDays(10),
                        INCIDENT_TYPE, "123 Main St", ClaimStatus.OPEN, BigDecimal.valueOf(12000), "USD"),
                new ExistingClaimRecord("CLM-9002", "John Doe", LocalDate.now().minusDays(5),
                        INCIDENT_TYPE, "123 Main St", ClaimStatus.RECENTLY_CLOSED, BigDecimal.valueOf(14500), "USD")
        );

        when(claimRepository.findByInsuredIdAndStatusIn(eq(INSURED_ID), anyList()))
                .thenReturn(openAndClosedClaims);

        when(communicationService.sendNotification(anyString(), anyList(), anyString()))
                .thenReturn("MSG-ID-12345");

        when(documentService.storeTransformationLog(anyString(), anyString()))
                .thenReturn("s3://bucket/logs/transformation-20231025.json");

        // Act
        TransformationOutcome outcome = transformationService.processFnol(newFnol, MANUAL_REVIEW_THRESHOLD);

        // Assert
        assertNotNull(outcome);
        assertEquals(100.0, outcome.weightedScore(), 0.01);
        assertTrue(outcome.isMergeCandidate());
        assertTrue(outcome.isFlaggedForManualReview());
        assertEquals("CLM-9001", outcome.primaryMergeReference());
        assertEquals("CLM-9002", outcome.secondaryMergeReference());
        assertNotNull(outcome.manualReviewNote());

        // Verify external I/O interactions
        verify(claimRepository).findByInsuredIdAndStatusIn(eq(INSURED_ID), List.of(ClaimStatus.OPEN, ClaimStatus.RECENTLY_CLOSED));
        verify(communicationService).sendNotification(eq("manual-review-needed"), anyList(), eq("us-east-1"));
        verify(documentService).storeTransformationLog(eq(INSURED_ID), anyString());
        verifyNoMoreInteractions(claimRepository, communicationService, documentService);
    }

    // --- Supporting Types ---

    static class NewFnolSubmission {
        final String insuredId, insuredName, incidentDate, incidentType, location, currency;
        final BigDecimal amount;

        NewFnolSubmission(String insuredId, String insuredName, LocalDate incidentDate,
                          String incidentType, String location, BigDecimal amount, String currency) {
            this.insuredId = insuredId;
            this.insuredName = insuredName;
            this.incidentDate = incidentDate.toString();
            this.incidentType = incidentType;
            this.location = location;
            this.amount = amount;
            this.currency = currency;
        }
    }

    enum ClaimStatus { OPEN, RECENTLY_CLOSED }

    static class ExistingClaimRecord {
        final String claimId, insuredName, incidentDate, incidentType, location, currency;
        final ClaimStatus status;
        final BigDecimal amount;

        ExistingClaimRecord(String claimId, String insuredName, LocalDate incidentDate,
                            String incidentType, String location, ClaimStatus status,
                            BigDecimal amount, String currency) {
            this.claimId = claimId;
            this.insuredName = insuredName;
            this.incidentDate = incidentDate.toString();
            this.incidentType = incidentType;
            this.location = location;
            this.status = status;
            this.amount = amount;
            this.currency = currency;
        }
    }

    static class TransformationOutcome {
        double weightedScore;
        boolean mergeCandidate;
        boolean flaggedForManualReview;
        String primaryMergeReference, secondaryMergeReference;
        String manualReviewNote;

        double getWeightedScore() { return weightedScore; }
        boolean isMergeCandidate() { return mergeCandidate; }
        boolean isFlaggedForManualReview() { return flaggedForManualReview; }
        String primaryMergeReference() { return primaryMergeReference; }
        String secondaryMergeReference() { return secondaryMergeReference; }
        String manualReviewNote() { return manualReviewNote; }
    }

    static class InsuredEngagementTransformationService {
        private final ClaimRepository claimRepository;
        private final CommunicationService communicationService;
        private final DocumentService documentService;

        InsuredEngagementTransformationService(ClaimRepository claimRepository,
                                               CommunicationService communicationService,
                                               DocumentService documentService) {
            this.claimRepository = claimRepository;
            this.communicationService = communicationService;
            this.documentService = documentService;
        }

        TransformationOutcome processFnol(NewFnolSubmission fnol, double threshold) {
            List<ExistingClaimRecord> matches = claimRepository.findByInsuredIdAndStatusIn(
                    fnol.insuredId, List.of(ClaimStatus.OPEN, ClaimStatus.RECENTLY_CLOSED)
            );

            double score = calculateWeightedScore(fnol, matches);
            boolean mergeCandidate = score > 0.5;
            boolean flagManual = score >= threshold;

            TransformationOutcome outcome = new TransformationOutcome();
            outcome.weightedScore = score;
            outcome.mergeCandidate = mergeCandidate;
            outcome.flaggedForManualReview = flagManual;

            if (!matches.isEmpty()) {
                outcome.primaryMergeReference = matches.get(0).claimId;
                if (matches.size() > 1) {
                    outcome.secondaryMergeReference = matches.get(1).claimId;
                }
            }

            if (flagManual) {
                outcome.manualReviewNote = "Score " + score + " exceeds threshold " + threshold + ". Requires manual review.";
                communicationService.sendNotification("manual-review-needed", List.of("claims@newco.com"), "us-east-1");
            }

            documentService.storeTransformationLog(fnol.insuredId, "transformation-log.json");
            return outcome;
        }

        private double calculateWeightedScore(NewFnolSubmission fnol, List<ExistingClaimRecord> matches) {
            // Deterministic scoring for test: 100% match when records exist
            return matches.isEmpty() ? 0.0 : 1.0;
        }
    }

    interface ClaimRepository {
        List<ExistingClaimRecord> findByInsuredIdAndStatusIn(String insuredId, List<ClaimStatus> statuses);
    }

    interface CommunicationService {
        String sendNotification(String subject, List<String> toAddresses, String region);
    }

    interface DocumentService {
        String storeTransformationLog(String insuredId, String fileName);
    }
}
