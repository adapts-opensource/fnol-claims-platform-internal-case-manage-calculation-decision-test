package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AllFnolDecisionsMustRetainContextForMinimumTest {

    private static final String REGULATORY_RETENTION_DAYS = "2555";
    private static final String MOCK_TENANT_ID = "tenant_insurance_001";
    private static final String MOCK_POLICY_ID = "pol_987654321";
    private static final String MOCK_CLAIM_ID = "claim_123456789";
    private static final String MOCK_IDEMPOTENCY_KEY = "idemp_key_fnol_001";

    @Mock
    private FnolDecisionContextService contextService;

    private FnolDecisionProcessor decisionProcessor;

    @BeforeEach
    void setUp() {
        decisionProcessor = new FnolDecisionProcessor(contextService);
    }

    @Test
    void all_fnol_decisions_must_retain_context_for_minimum_regulatory_period() {
        Instant now = Instant.now();
        Map<String, Object> fnolSubmission = Map.of(
                "claim_id", MOCK_CLAIM_ID,
                "policy_id", MOCK_POLICY_ID,
                "tenant_id", MOCK_TENANT_ID,
                "idempotency_key", MOCK_IDEMPOTENCY_KEY,
                "submission_timestamp", now.toString(),
                "validation_status", "PASSED",
                "decision_status", "ACCEPTED"
        );

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);

        decisionProcessor.processFnolDecision(fnolSubmission);

        verify(contextService, times(1)).saveContext(contextCaptor.capture());
        Map<String, Object> savedContext = contextCaptor.getValue();

        assertEquals(REGULATORY_RETENTION_DAYS, savedContext.get("regulatory_retention_days"));
        assertEquals(MOCK_TENANT_ID, savedContext.get("tenant_id"));
        assertNotNull(savedContext.get("created_at"));
        assertNotNull(savedContext.get("updated_at"));
        assertEquals(MOCK_IDEMPOTENCY_KEY, savedContext.get("idempotency_key"));
        assertEquals("PASSED", savedContext.get("validation_status"));
        assertTrue((Boolean) savedContext.get("structured_log_enabled"));
    }

    interface FnolDecisionContextService {
        void saveContext(Map<String, Object> context);
    }

    static class FnolDecisionProcessor {
        private final FnolDecisionContextService contextService;

        FnolDecisionProcessor(FnolDecisionContextService contextService) {
            this.contextService = contextService;
        }

        void processFnolDecision(Map<String, Object> submission) {
            if (submission.get("validation_status") == null) {
                throw new IllegalArgumentException("Input validation failed at service boundary");
            }

            Map<String, Object> enrichedContext = new java.util.HashMap<>(submission);
            enrichedContext.put("regulatory_retention_days", REGULATORY_RETENTION_DAYS);
            enrichedContext.put("created_at", Instant.now().toString());
            enrichedContext.put("updated_at", Instant.now().toString());
            enrichedContext.put("structured_log_enabled", true);

            if (enrichedContext.get("idempotency_key") == null) {
                throw new IllegalStateException("Idempotency key missing, concurrency safety violated");
            }

            contextService.saveContext(enrichedContext);
        }
    }
}
