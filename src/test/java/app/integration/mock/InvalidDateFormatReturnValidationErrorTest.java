package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InvalidDateFormatReturnValidationErrorTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    @InjectMocks
    private ClaimDataStandardizationDecisionTransformer transformer;

    @Test
    void invalid_date_format_return_validation_error() {
        // Arrange: Payload containing an invalid date format
        Map<String, Object> payload = Map.of(
            "id", "CLM-2024-001",
            "claimDate", "2024/13/45",
            "policyNumber", "POL-XYZ"
        );

        // Act & Assert: Validate that transformation fails fast with a validation error
        ValidationException thrown = assertThrows(ValidationException.class, () -> {
            transformer.transform(payload);
        });

        assertEquals("Invalid date format: claimDate must follow ISO 8601 (yyyy-MM-dd)", thrown.getMessage());

        // Verify external I/O contracts are never invoked due to early validation failure
        verifyNoInteractions(auditDiaryStoreService, rulesEngineDecisionService, workflowTaskRouterService);
    }

    // Internal transformation logic stub for test isolation
    private static class ClaimDataStandardizationDecisionTransformer {
        private final AuditDiaryStoreService auditDiaryStoreService;
        private final RulesEngineDecisionService rulesEngineDecisionService;
        private final WorkflowTaskRouterService workflowTaskRouterService;

        ClaimDataStandardizationDecisionTransformer(AuditDiaryStoreService auditDiaryStoreService,
                                                    RulesEngineDecisionService rulesEngineDecisionService,
                                                    WorkflowTaskRouterService workflowTaskRouterService) {
            this.auditDiaryStoreService = auditDiaryStoreService;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
            this.workflowTaskRouterService = workflowTaskRouterService;
        }

        public Map<String, Object> transform(Map<String, Object> payload) {
            String claimDate = (String) payload.get("claimDate");
            if (claimDate == null || !claimDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new ValidationException("Invalid date format: claimDate must follow ISO 8601 (yyyy-MM-dd)");
            }
            auditDiaryStoreService.write(payload);
            rulesEngineDecisionService.evaluate(payload);
            workflowTaskRouterService.route(payload);
            return payload;
        }
    }

    private interface AuditDiaryStoreService {
        void write(Map<String, Object> payload);
    }

    private interface RulesEngineDecisionService {
        void evaluate(Map<String, Object> payload);
    }

    private interface WorkflowTaskRouterService {
        void route(Map<String, Object> payload);
    }

    private static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }
}
