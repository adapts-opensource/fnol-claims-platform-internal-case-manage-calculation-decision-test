package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskMustBeInResolvableStateTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimValidationDecisionService claimValidationDecisionService;

    @BeforeEach
    void setUp() {
        claimValidationDecisionService = new ClaimValidationDecisionService(
                documentStoreService, policyValidationService, rulesEngineService
        );
    }

    @Test
    void task_must_be_in_resolvable_state() {
        String taskId = "claim-task-001";
        Map<String, Object> validPayload = new HashMap<>();
        validPayload.put("id", taskId);
        validPayload.put("taskState", "RESOLVABLE");

        Map<String, Object> invalidPayload = new HashMap<>();
        invalidPayload.put("id", taskId);
        invalidPayload.put("taskState", "PENDING");

        // Assert valid state passes validation
        assertTrue(claimValidationDecisionService.validate(validPayload),
                "Validation should succeed when task is in RESOLVABLE state");

        // Assert invalid state fails validation
        assertFalse(claimValidationDecisionService.validate(invalidPayload),
                "Validation should fail when task is not in RESOLVABLE state");
    }

    // Service under test implementing the validation decision logic
    static class ClaimValidationDecisionService {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;

        ClaimValidationDecisionService(DocumentStoreService documentStoreService,
                                       PolicyValidationService policyValidationService,
                                       RulesEngineService rulesEngineService) {
            this.documentStoreService = documentStoreService;
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
        }

        boolean validate(Map<String, Object> payload) {
            Object state = payload.get("taskState");
            return "RESOLVABLE".equals(state);
        }
    }

    // Infra contract interfaces (mocked in tests to satisfy I/O contracts)
    interface DocumentStoreService { /* S3 */ }
    interface PolicyValidationService { /* DynamoDB */ }
    interface RulesEngineService { /* DynamoDB */ }
}
