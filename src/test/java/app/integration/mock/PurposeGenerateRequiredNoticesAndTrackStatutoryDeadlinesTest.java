package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock test for Claim Initiation & Routing:decision:calculation.
 * Verifies notice generation and statutory deadline tracking logic.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private JurisdictionRuleProvider jurisdictionRuleProvider;

    @Mock
    private NoticeGenerator noticeGenerator;

    @Mock
    private DeadlineTracker deadlineTracker;

    @InjectMocks
    private DecisionCalculator decisionCalculator;

    @Captor
    private ArgumentCaptor<NoticeRequest> noticeRequestCaptor;

    @Captor
    private ArgumentCaptor<DeadlineRequest> deadlineRequestCaptor;

    @BeforeEach
    void setUp() {
        // Reset mocks if necessary; MockitoExtension handles cleanup per test.
    }

    @Test
    @DisplayName("purpose_generate_required_notices_and_track_statutory_deadlines_based_on_jurisdiction_and_claim_type")
    void purpose_generate_required_notices_and_track_statutory_deadlines_based_on_jurisdiction_and_claim_type() {
        // Arrange
        String jurisdiction = "CA";
        String claimType = "AUTO";
        Map<String, Object> payload = Map.of(
                "claimType", claimType,
                "jurisdiction", jurisdiction,
                "claimId", "CLM-12345",
                "initiationTimestamp", Instant.now()
        );

        // Mock expected rules for the specific jurisdiction and claim type
        List<String> expectedNotices = List.of(
                "NOTICE_STATUTORY_DISCLOSURE",
                "NOTICE_FRAUD_WARNING",
                "NOTICE_SETTLEMENT_TIMELINE"
        );
        List<String> expectedDeadlines = List.of(
                "DEADLINE_RESPOND_WITHIN_30_DAYS",
                "DEADLINE_INSPECT_WITHIN_14_DAYS"
        );

        RulesResult rulesResult = new RulesResult(expectedNotices, expectedDeadlines);
        when(jurisdictionRuleProvider.resolveRules(jurisdiction, claimType))
                .thenReturn(rulesResult);

        // Act
        DecisionResult result = decisionCalculator.calculate(payload);

        // Assert: Verify calculation results
        assertNotNull(result, "DecisionResult should not be null");
        assertEquals(expectedNotices, result.getRequiredNotices(), "Notices should match jurisdiction/claim type rules");
        assertEquals(expectedDeadlines, result.getStatutoryDeadlines(), "Deadlines should match jurisdiction/claim type rules");

        // Assert: Verify external I/O interactions (Generate and Track)
        // Verify notices were generated
        verify(noticeGenerator, expectedNotices.size()).generate(noticeRequestCaptor.capture());
        List<NoticeRequest> capturedNotices = noticeRequestCaptor.getAllValues();
        assertEquals(expectedNotices.size(), capturedNotices.size(), "Number of generated notices should match expected count");
        capturedNotices.forEach(nr -> {
            assertEquals("CLM-12345", nr.getClaimId());
            assertNotNull(nr.getJurisdiction());
            assertNotNull(nr.getClaimType());
        });

        // Verify deadlines were tracked
        verify(deadlineTracker, expectedDeadlines.size()).track(deadlineRequestCaptor.capture());
        List<DeadlineRequest> capturedDeadlines = deadlineRequestCaptor.getAllValues();
        assertEquals(expectedDeadlines.size(), capturedDeadlines.size(), "Number of tracked deadlines should match expected count");
        capturedDeadlines.forEach(dr -> {
            assertEquals("CLM-12345", dr.getClaimId());
            assertNotNull(dr.getDeadlineRule());
            assertNotNull(dr.getTriggerTimestamp());
        });
    }

    // --- Stubbed Interfaces and Classes for Mock Compilation ---
    // In a real project, these would be defined in the application package.

    interface JurisdictionRuleProvider {
        RulesResult resolveRules(String jurisdiction, String claimType);
    }

    interface NoticeGenerator {
        void generate(NoticeRequest request);
    }

    interface DeadlineTracker {
        void track(DeadlineRequest request);
    }

    static class DecisionCalculator {
        private final JurisdictionRuleProvider jurisdictionRuleProvider;
        private final NoticeGenerator noticeGenerator;
        private final DeadlineTracker deadlineTracker;

        DecisionCalculator(JurisdictionRuleProvider jurisdictionRuleProvider,
                           NoticeGenerator noticeGenerator,
                           DeadlineTracker deadlineTracker) {
            this.jurisdictionRuleProvider = jurisdictionRuleProvider;
            this.noticeGenerator = noticeGenerator;
            this.deadlineTracker = deadlineTracker;
        }

        DecisionResult calculate(Map<String, Object> payload) {
            String jurisdiction = (String) payload.get("jurisdiction");
            String claimType = (String) payload.get("claimType");
            String claimId = (String) payload.get("claimId");
            Instant timestamp = (Instant) payload.get("initiationTimestamp");

            RulesResult rules = jurisdictionRuleProvider.resolveRules(jurisdiction, claimType);

            // Generate notices
            for (String noticeType : rules.notices()) {
                noticeGenerator.generate(new NoticeRequest(claimId, jurisdiction, claimType, noticeType));
            }

            // Track deadlines
            for (String deadlineRule : rules.deadlines()) {
                deadlineTracker.track(new DeadlineRequest(claimId, deadlineRule, timestamp));
            }

            return new DecisionResult(rules.notices(), rules.deadlines());
        }
    }

    record RulesResult(List<String> notices, List<String> deadlines) {}

    record DecisionResult(List<String> requiredNotices, List<String> statutoryDeadlines) {}

    record NoticeRequest(String claimId, String jurisdiction, String claimType, String noticeType) {}

    record DeadlineRequest(String claimId, String deadlineRule, Instant triggerTimestamp) {}
}
