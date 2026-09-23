package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NFR Section:
 * - Input Validation: Enforces required/optional fields and business rules.
 * - Thread Safety: Stateless validation service; mocks ensure deterministic execution.
 * - Compliance: GDPR/SOC2 respected via mocked PII handling; no real data touched.
 * - Observability: Structured logging implied via service layer; test focuses on contract validation.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private CandidateListService candidateListService;

    @Mock
    private PolicyAdministrationService pasService;

    @Mock
    private CacheService cacheService;

    private ClaimDecisionValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ClaimDecisionValidationService(candidateListService, pasService, cacheService);
    }

    @Test
    void testInputCriteriaRequiredAndPolicyIdValidation() {
        // Missing required 'selected_policy_id'
        Map<String, Object> incompletePayload = Map.of(
                "fnol_id", "FNOL-123",
                "adjuster_id", "ADJ-456"
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validateInput(incompletePayload));
    }

    @Test
    void testPolicyIdMustBeInCandidateList() {
        Map<String, Object> payload = Map.of(
                "fnol_id", "FNOL-123",
                "selected_policy_id", "POL-INVALID",
                "adjuster_id", "ADJ-456"
        );
        when(candidateListService.getCandidates(anyString())).thenReturn(List.of("POL-CANDIDATE-01"));
        assertThrows(IllegalArgumentException.class, () -> validationService.validateInput(payload));
    }

    @Test
    void testFreshnessRequirementsPolicyDetailsFromPas() {
        Map<String, Object> payload = Map.of(
                "fnol_id", "FNOL-123",
                "selected_policy_id", "POL-CANDIDATE-01",
                "adjuster_id", "ADJ-456"
        );
        when(candidateListService.getCandidates(anyString())).thenReturn(List.of("POL-CANDIDATE-01"));
        // Simulate stale/missing PAS data to trigger freshness validation failure
        when(pasService.fetchPolicyDetails(eq("POL-CANDIDATE-01"))).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> validationService.validateInput(payload));
    }

    @Test
    void testOptionalResolutionNotes() {
        Map<String, Object> validPayload = Map.of(
                "fnol_id", "FNOL-123",
                "selected_policy_id", "POL-CANDIDATE-01",
                "adjuster_id", "ADJ-456",
                "resolution_notes", "Initial triage complete. Awaiting adjuster assignment."
        );
        when(candidateListService.getCandidates(anyString())).thenReturn(List.of("POL-CANDIDATE-01"));
        when(pasService.fetchPolicyDetails(eq("POL-CANDIDATE-01")))
                .thenReturn(Map.of("policy_status", "ACTIVE", "coverage_type", "AUTO"));

        assertDoesNotThrow(() -> validationService.validateInput(validPayload));
    }
}
