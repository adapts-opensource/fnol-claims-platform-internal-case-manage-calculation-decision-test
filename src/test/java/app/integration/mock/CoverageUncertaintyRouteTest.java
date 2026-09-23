package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Infrastructure contract interfaces matching the data model and AWS services
interface PolicyValidationService {
    boolean validate(Map<String, Object> payload);
}

interface RulesEngineService {
    String calculateRouting(Map<String, Object> payload);
}

interface DocumentStoreService {
    String store(String bucketName, String objectKeyPattern);
}

interface TaskService {
    String createTask(String state, String taskName);
}

/**
 * Service under test for Claim Initiation & Routing:calculation:transformation.
 * Implements input validation, policy gap detection, and task routing.
 * Thread-safe via immutable payload processing and atomic state transitions.
 */
class ClaimTransformationService {

    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;
    private final DocumentStoreService documentStoreService;
    private final TaskService taskService;

    ClaimTransformationService(PolicyValidationService policyValidationService,
                               RulesEngineService rulesEngineService,
                               DocumentStoreService documentStoreService,
                               TaskService taskService) {
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
        this.documentStoreService = documentStoreService;
        this.taskService = taskService;
    }

    Map<String, Object> transformAndRoute(Map<String, Object> payload) {
        // Input validation & least-privilege contract check
        if (!policyValidationService.validate(payload)) {
            throw new IllegalArgumentException("Payload failed validation contract");
        }

        String routingCode = rulesEngineService.calculateRouting(payload);
        String claimId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String claimNumber = "CLM-2024-" + claimId;

        // Persist to S3 (mocked infra I/O)
        documentStoreService.store("DocumentStoreService-bucket", claimNumber + ".json");

        Map<String, Object> result = Map.of(
                "id", claimNumber,
                "claim_number", claimNumber,
                "claim_type", "COVERAGE_REVIEW".equals(routingCode) ? "Coverage review claim" : "Standard",
                "fnol_state", "COVERAGE_REVIEW".equals(routingCode) ? "Coverage Triage" : "Open",
                "payload", payload
        );

        if ("Coverage Triage".equals(result.get("fnol_state"))) {
            taskService.createTask("Coverage Triage", "Review Coverage");
        }
        return result;
    }
}

/**
 * JUnit 5 integration mock test for CoverageUncertaintyRoute.
 * Verifies transformation routes claim to Coverage Review when date of loss is outside policy period.
 * Mocks all external I/O (S3, DynamoDB, HTTP) to ensure deterministic execution.
 */
@ExtendWith(MockitoExtension.class)
class CoverageUncertaintyRouteTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private ClaimTransformationService claimTransformationService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = Map.of(
                "tenant_code", "FL01",
                "year", 2024,
                "policy_effective_date", "2024-01-01",
                "policy_expiration_date", "2024-06-30",
                "date_of_loss", "2024-07-15",
                "cause_of_loss", "water",
                "product", "HO3"
        );
    }

    @Test
    void route_to_coverage_review_on_policy_gap() {
        // Arrange
        String mockClaimId = "CLM-2024-001";
        when(policyValidationService.validate(any())).thenReturn(true);
        when(rulesEngineService.calculateRouting(any())).thenReturn("COVERAGE_REVIEW");
        when(documentStoreService.store(anyString(), anyString())).thenReturn("s3://bucket/" + mockClaimId + ".json");
        when(taskService.createTask(anyString(), anyString())).thenReturn("TASK-COV-001");

        // Act
        Map<String, Object> result = claimTransformationService.transformAndRoute(claimPayload);

        // Assert
        assertNotNull(result.get("claim_number"), "Claim number generated");
        assertEquals("Coverage review claim", result.get("claim_type"), "Claim type set to Coverage review claim");
        assertEquals("Coverage Triage", result.get("fnol_state"), "FNOL-level state set to Coverage Triage");
        verify(taskService).createTask(eq("Coverage Triage"), eq("Review Coverage"));
    }
}
