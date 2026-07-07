package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

// Minimal repository interface mocking external DynamoDB/S3 data store
interface FnolSubmissionRepository {
    Optional<Map<String, Object>> findById(String id);
}

// System under test: state transition calculator for FNOL submissions
class StateTransitionCalculator {
    private final FnolSubmissionRepository repository;

    StateTransitionCalculator(FnolSubmissionRepository repository) {
        this.repository = repository;
    }

    // Validates that Date of Loss (DoL) falls within the active policy period
    void validatePolicyPeriod(String claimId) {
        Map<String, Object> payload = repository.findById(claimId)
            .orElseThrow(() -> new IllegalArgumentException("Claim record not found: " + claimId));

        LocalDate dateOfLoss = LocalDate.parse((String) payload.get("dateOfLoss"));
        LocalDate policyStart = LocalDate.parse((String) payload.get("policyStartDate"));
        LocalDate policyEnd = LocalDate.parse((String) payload.get("policyEndDate"));

        if (dateOfLoss.isBefore(policyStart) || dateOfLoss.isAfter(policyEnd)) {
            throw new IllegalArgumentException("DoL must be within active policy period");
        }
    }
}

@ExtendWith(MockitoExtension.class)
class DolMustBeWithinActivePolicyPeriodTest {

    @Mock
    private FnolSubmissionRepository submissionRepository;

    private StateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new StateTransitionCalculator(submissionRepository);
    }

    @Test
    @DisplayName("dol_must_be_within_active_policy_period")
    void dol_must_be_within_active_policy_period_valid() {
        String claimId = "fnol-claim-001";
        Map<String, Object> payload = Map.of(
            "dateOfLoss", "2023-10-15",
            "policyStartDate", "2023-01-01",
            "policyEndDate", "2023-12-31"
        );
        when(submissionRepository.findById(claimId)).thenReturn(Optional.of(payload));

        assertDoesNotThrow(() -> calculator.validatePolicyPeriod(claimId));
    }

    @Test
    @DisplayName("dol_must_be_within_active_policy_period_boundary_before_start")
    void dol_must_be_within_active_policy_period_boundary_before_start() {
        String claimId = "fnol-claim-002";
        Map<String, Object> payload = Map.of(
            "dateOfLoss", "2022-12-31",
            "policyStartDate", "2023-01-01",
            "policyEndDate", "2023-12-31"
        );
        when(submissionRepository.findById(claimId)).thenReturn(Optional.of(payload));

        assertThrows(IllegalArgumentException.class, () -> calculator.validatePolicyPeriod(claimId));
    }

    @Test
    @DisplayName("dol_must_be_within_active_policy_period_boundary_after_end")
    void dol_must_be_within_active_policy_period_boundary_after_end() {
        String claimId = "fnol-claim-003";
        Map<String, Object> payload = Map.of(
            "dateOfLoss", "2024-01-01",
            "policyStartDate", "2023-01-01",
            "policyEndDate", "2023-12-31"
        );
        when(submissionRepository.findById(claimId)).thenReturn(Optional.of(payload));

        assertThrows(IllegalArgumentException.class, () -> calculator.validatePolicyPeriod(claimId));
    }
}
