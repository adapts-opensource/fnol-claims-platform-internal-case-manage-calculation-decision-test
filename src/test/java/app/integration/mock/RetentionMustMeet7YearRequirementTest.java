package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RetentionMustMeet7YearRequirementTest {

    @Mock
    private RetentionPolicyValidator retentionPolicyValidator;

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @Test
    void retention_must_meet_7_year_requirement() {
        // Given: A state transition request where the insured engagement record
        // has an incident date less than 7 years ago, violating retention policy.
        String exposureId = "EXP-TEST-001";
        LocalDate incidentDate = LocalDate.now().minusYears(5);
        Map<String, Object> transitionPayload = Map.of(
            "exposure_id", exposureId,
            "incident_date", incidentDate.toString(),
            "target_state", "ARCHIVED"
        );

        // Mock the retention check to return false (does not meet 7-year requirement)
        when(retentionPolicyValidator.meetsSevenYearRetention(exposureId, incidentDate))
                .thenReturn(false);

        // When & Then: State transition must fail with a policy violation exception
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> StateTransitionEngine.executeTransition(transitionPayload, retentionPolicyValidator, stateTransitionRepository)
        );
        assertEquals("Retention policy violation: Insured engagement records must meet a 7-year retention requirement before state transition.", thrown.getMessage());

        // Verify retention policy was explicitly checked
        verify(retentionPolicyValidator).meetsSevenYearRetention(exposureId, incidentDate);
        // Verify repository was never accessed due to early validation failure
        verifyNoInteractions(stateTransitionRepository);
    }

    @Test
    void retention_must_meet_7_year_requirement_passes() {
        // Given: A state transition request where retention period meets/exceeds 7 years
        String exposureId = "EXP-TEST-001";
        LocalDate incidentDate = LocalDate.now().minusYears(7);
        Map<String, Object> transitionPayload = Map.of(
            "exposure_id", exposureId,
            "incident_date", incidentDate.toString(),
            "target_state", "ARCHIVED"
        );

        // Mock the retention check to pass
        when(retentionPolicyValidator.meetsSevenYearRetention(exposureId, incidentDate))
                .thenReturn(true);

        // Mock successful repository update
        doNothing().when(stateTransitionRepository).persistStateChange(eq(exposureId), eq("ARCHIVED"));

        // When & Then: Transition should succeed without throwing
        assertDoesNotThrow(() -> StateTransitionEngine.executeTransition(transitionPayload, retentionPolicyValidator, stateTransitionRepository));

        // Verify retention policy was checked and repository was updated
        verify(retentionPolicyValidator).meetsSevenYearRetention(exposureId, incidentDate);
        verify(stateTransitionRepository).persistStateChange(exposureId, "ARCHIVED");
    }
}

// Minimal domain interfaces/classes for test compilation and isolation
class RetentionPolicyValidator {
    public boolean meetsSevenYearRetention(String exposureId, LocalDate incidentDate) {
        return java.time.temporal.ChronoUnit.YEARS.between(incidentDate, LocalDate.now()) >= 7;
    }
}

interface StateTransitionRepository {
    void persistStateChange(String exposureId, String targetState);
}

class StateTransitionEngine {
    public static void executeTransition(Map<String, Object> payload, RetentionPolicyValidator validator, StateTransitionRepository repository) {
        String exposureId = (String) payload.get("exposure_id");
        LocalDate incidentDate = LocalDate.parse((String) payload.get("incident_date"));
        String targetState = (String) payload.get("target_state");

        if (!validator.meetsSevenYearRetention(exposureId, incidentDate)) {
            throw new IllegalArgumentException("Retention policy violation: Insured engagement records must meet a 7-year retention requirement before state transition.");
        }
        repository.persistStateChange(exposureId, targetState);
    }
}
