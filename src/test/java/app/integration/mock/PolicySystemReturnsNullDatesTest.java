package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

class InsuredEngagementOrchestrationDecisionMockTest {

    @Mock
    private PolicySystemClient policySystemClient;

    @InjectMocks
    private InsuredEngagementOrchestrator insuredEngagementOrchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void policy_system_returns_null_dates() {
        // Arrange
        String insuredId = "INS-9921";
        when(policySystemClient.getCoverageDates(insuredId)).thenReturn(new CoverageDates(null, null));

        // Act
        DecisionResult result = insuredEngagementOrchestrator.evaluateEngagement(insuredId);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertEquals(DecisionStatus.PENDING_REVIEW, result.status());
        assertNull(result.effectiveDate(), "Effective date must be null when policy system returns null");
        assertNull(result.expirationDate(), "Expiration date must be null when policy system returns null");
        verify(policySystemClient).getCoverageDates(insuredId);
    }

    // Supporting types for compilation context
    record CoverageDates(LocalDate effectiveDate, LocalDate expirationDate) {}
    record DecisionResult(DecisionStatus status, LocalDate effectiveDate, LocalDate expirationDate) {}
    interface PolicySystemClient { CoverageDates getCoverageDates(String insuredId); }
    class InsuredEngagementOrchestrator {
        private final PolicySystemClient policySystemClient;
        InsuredEngagementOrchestrator(PolicySystemClient policySystemClient) {
            this.policySystemClient = policySystemClient;
        }
        DecisionResult evaluateEngagement(String insuredId) {
            CoverageDates dates = policySystemClient.getCoverageDates(insuredId);
            if (dates == null || dates.effectiveDate() == null || dates.expirationDate() == null) {
                return new DecisionResult(DecisionStatus.PENDING_REVIEW, null, null);
            }
            return new DecisionResult(DecisionStatus.APPROVED, dates.effectiveDate(), dates.expirationDate());
        }
    }
}
