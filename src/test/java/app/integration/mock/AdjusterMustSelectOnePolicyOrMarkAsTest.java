package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolValidationMockTest {

    @Mock
    private FnolOrchestrationValidationService validationService;

    private FnolSubmissionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FnolSubmissionValidator(validationService);
    }

    @Test
    void adjuster_must_select_one_policy_or_mark_as_truly_unmatched() {
        // Negative case: Adjuster provides neither a policy nor the unmatched flag
        Map<String, Object> invalidPayload = Map.of("policyId", null, "trulyUnmatched", false);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidPayload));

        // Positive case 1: Adjuster selects exactly one policy
        Map<String, Object> validPolicyPayload = Map.of("policyId", "POL-8842", "trulyUnmatched", false);
        doNothing().when(validationService).persistValidationResult(any(Map.class));
        assertDoesNotThrow(() -> validator.validate(validPolicyPayload));

        // Positive case 2: Adjuster marks claim as truly unmatched
        Map<String, Object> unmatchedPayload = Map.of("policyId", null, "trulyUnmatched", true);
        assertDoesNotThrow(() -> validator.validate(unmatchedPayload));
    }
}

// Minimal supporting classes for test isolation and compilation
interface FnolOrchestrationValidationService {
    void persistValidationResult(Map<String, Object> payload);
}

class FnolSubmissionValidator {
    private final FnolOrchestrationValidationService service;

    FnolSubmissionValidator(FnolOrchestrationValidationService service) {
        this.service = service;
    }

    void validate(Map<String, Object> payload) {
        boolean hasPolicy = payload.get("policyId") != null && !payload.get("policyId").toString().isEmpty();
        boolean isUnmatched = Boolean.TRUE.equals(payload.get("trulyUnmatched"));

        if (!hasPolicy && !isUnmatched) {
            throw new IllegalArgumentException("Adjuster must select one policy or mark as truly unmatched");
        }
        // Mocked external I/O: persists to DynamoDB/S3 via service layer
        service.persistValidationResult(payload);
    }
}
