package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionValidationDecisionBusinessRulesTest {

    @Mock
    private ClaimValidationService mockValidator;
    @Mock
    private IdempotencyLockService mockIdempotency;
    @Mock
    private ClaimRepository mockRepository;
    @Mock
    private StructuredLogger mockLogger;

    private FnolSubmissionService fnolService;

    @BeforeEach
    void setUp() {
        fnolService = new FnolSubmissionService(mockValidator, mockIdempotency, mockRepository, mockLogger);
    }

    @Test
    void business_rules_49() {
        // Business Rule 49: Valid multi-channel FNOL submission with idempotency key and required fields must result in APPROVED decision
        String idempotencyKey = UUID.randomUUID().toString();
        String tenantId = "newco_tenant_001";
        String policyId = "POL-2024-789";
        String claimNumber = "CLM-2024-100";

        Map<String, Object> submissionPayload = Map.of(
            "tenant_id", tenantId,
            "policy_id", policyId,
            "claim_number", claimNumber,
            "created_at", Instant.now().toString(),
            "audit_tenant", tenantId,
            "idempotency_key", idempotencyKey
        );

        when(mockIdempotency.tryAcquire(anyString())).thenReturn(true);
        when(mockValidator.validateInput(any())).thenReturn(true);
        when(mockRepository.persist(any())).thenAnswer(invocation -> {
            var item = invocation.getArgument(0, Map.class);
            item.put("claim_id", UUID.randomUUID().toString());
            return item;
        });

        Map<String, Object> decision = fnolService.evaluateDecision(submissionPayload);

        assertEquals("APPROVED", decision.get("decision"));
        assertNotNull(decision.get("claim_id"));
        assertEquals(tenantId, decision.get("tenant_id"));
        assertEquals(policyId, decision.get("policy_id"));
        assertNotNull(decision.get("created_at"));
        verify(mockRepository, times(1)).persist(any());
        verify(mockIdempotency, times(1)).release(anyString());
        verify(mockLogger, times(1)).info(eq("FNOL_SUBMISSION_SUCCESS"), anyString(), eq("APPROVED"));
    }

    // Minimal interface definitions for test compilation context
    interface ClaimValidationService { boolean validateInput(Map<String, Object> payload); }
    interface IdempotencyLockService { boolean tryAcquire(String key); void release(String key); }
    interface ClaimRepository { Map<String, Object> persist(Map<String, Object> item); }
    interface StructuredLogger { void info(String event, String message, String decision); }

    static class FnolSubmissionService {
        private final ClaimValidationService validator;
        private final IdempotencyLockService idempotency;
        private final ClaimRepository repository;
        private final StructuredLogger logger;

        FnolSubmissionService(ClaimValidationService validator, IdempotencyLockService idempotency, ClaimRepository repository, StructuredLogger logger) {
            this.validator = validator;
            this.idempotency = idempotency;
            this.repository = repository;
            this.logger = logger;
        }

        Map<String, Object> evaluateDecision(Map<String, Object> payload) {
            String idempotencyKey = (String) payload.get("idempotency_key");
            if (!idempotency.tryAcquire(idempotencyKey)) {
                return Map.of("decision", "DUPLICATE_REQUEST");
            }
            try {
                if (!validator.validateInput(payload)) {
                    return Map.of("decision", "INVALID_INPUT");
                }
                Map<String, Object> saved = repository.persist(payload);
                logger.info("FNOL_SUBMISSION_SUCCESS", "Claim validated and persisted", "APPROVED");
                return saved;
            } finally {
                idempotency.release(idempotencyKey);
            }
        }
    }
}
