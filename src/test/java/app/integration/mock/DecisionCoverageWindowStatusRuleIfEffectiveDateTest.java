package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionCoverageWindowStatusRuleIfEffectiveDateTest {

    private CoverageWindowStatusDecisionService decisionService;

    @Mock
    private CommunicationServices sesClient;
    @Mock
    private DataPersistence dynamoDbClient;
    @Mock
    private DocumentMediaStore s3Client;

    @BeforeEach
    void setUp() {
        decisionService = new CoverageWindowStatusDecisionService(sesClient, dynamoDbClient, s3Client);
    }

    @Test
    void decision_coverage_window_status_rule_if_effective_date_expiration_and_not_canceled_then_active_true_else_if_reinstated_and_date_reinstatement_then_active_true_else_active_false_n_expected_outcome_active_outside_period_moratorium_restricted() {
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);
        LocalDate currentDate = LocalDate.of(2024, 6, 15);
        boolean isCanceled = false;
        boolean isReinstated = false;
        LocalDate reinstatementDate = null;

        CoverageDecision decision = decisionService.evaluateCoverageWindowStatus(
                effectiveDate, expirationDate, currentDate, isCanceled, isReinstated, reinstatementDate
        );

        assertFalse(decision.isActive(), "Expected active to be false");
        assertTrue(decision.isOutsidePeriod(), "Expected outside_period to be true");
        assertTrue(decision.isMoratoriumRestricted(), "Expected moratorium_restricted to be true");
    }

    interface CommunicationServices {
        void sendEmail(String from, String to);
    }

    interface DataPersistence {
        void saveItem(String table, Object item);
    }

    interface DocumentMediaStore {
        void putObject(String bucket, String key, Object data);
    }

    static class CoverageWindowStatusDecisionService {
        private final CommunicationServices ses;
        private final DataPersistence dynamoDb;
        private final DocumentMediaStore s3;

        CoverageWindowStatusDecisionService(CommunicationServices ses, DataPersistence dynamoDb, DocumentMediaStore s3) {
            this.ses = ses;
            this.dynamoDb = dynamoDb;
            this.s3 = s3;
        }

        CoverageDecision evaluateCoverageWindowStatus(LocalDate effectiveDate, LocalDate expirationDate, LocalDate currentDate, boolean isCanceled, boolean isReinstated, LocalDate reinstatementDate) {
            if (effectiveDate != null && expirationDate != null && currentDate != null) {
                boolean withinWindow = !currentDate.isBefore(effectiveDate) && !currentDate.isAfter(expirationDate);
                if (withinWindow && !isCanceled) {
                    return new CoverageDecision(true, false, false);
                }
                if (isReinstated && reinstatementDate != null && !currentDate.isBefore(reinstatementDate)) {
                    return new CoverageDecision(true, false, false);
                }
            }
            return new CoverageDecision(false, true, true);
        }
    }

    record CoverageDecision(boolean active, boolean outsidePeriod, boolean moratoriumRestricted) {
        boolean isActive() { return active; }
        boolean isOutsidePeriod() { return outsidePeriod; }
        boolean isMoratoriumRestricted() { return moratoriumRestricted; }
    }
}
