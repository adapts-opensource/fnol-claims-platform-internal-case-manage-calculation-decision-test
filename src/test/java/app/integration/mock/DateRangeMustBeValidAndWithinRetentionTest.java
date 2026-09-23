package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 integration mock tests for Insured Engagement & Tracking: decision: transformation.
 * Validates input constraints, retention policies, and isolation from live infrastructure.
 * 
 * NFR Coverage:
 * - Input Validation: Date range checks.
 * - Compliance: Retention policy enforcement (GDPR/SOC2).
 * - Thread Safety: Stateless mocks, MockitoExtension.
 * - Security: No real AWS calls, mocked persistence.
 */
@ExtendWith(MockitoExtension.class)
public class InsuredEngagementDecisionTransformationTest {

    @Mock
    private ReserveLineRepository reserveLineRepository;

    @Mock
    private EngagementAuditLogger auditLogger;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    /**
     * Verifies that the transformation service rejects date ranges that are invalid
     * or fall outside the defined retention period.
     * 
     * Test Case Label: DateRangeMustBeValidAndWithinRetention
     * Description: Date range must be valid and within retention period
     */
    @Test
    void dateRangeMustBeValidAndWithinRetentionPeriod() {
        // Given: Date range outside retention policy (e.g., older than 7 years)
        // and/or invalid range (start > end).
        LocalDate today = LocalDate.now();
        LocalDate retentionLimit = today.minusYears(7);
        LocalDate invalidStartDate = retentionLimit.minusDays(1);
        LocalDate invalidEndDate = invalidStartDate.minusDays(1);

        // When: Attempting transformation with invalid dates
        assertThrows(IllegalArgumentException.class, () -> {
            decisionTransformationService.processDecisions(
                    invalidStartDate,
                    invalidEndDate
            );
        });

        // Then: Verify no side effects, persistence calls, or audit logs occur
        verify(reserveLineRepository, never()).findAllByDateRange(any());
        verify(reserveLineRepository, never()).save(any());
        verify(auditLogger, never()).logTransformation(any());
    }
}
