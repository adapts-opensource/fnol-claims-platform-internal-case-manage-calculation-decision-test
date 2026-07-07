package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration mock tests for Claim Initiation & Routing: decision:validation.
 * Verifies SIU referral logic based on fraud score and policy inception.
 */
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private DataStoreService dataStoreService;

    @Mock
    private TaskService taskService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ClaimInitiationService claimInitiationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("validate_siu_referral_candidate")
    void validate_siu_referral_candidate() {
        // Arrange: Inputs
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "POL-SIU");
        payload.put("loss_date", "2024-05-15");
        payload.put("cause_of_loss", "fire");
        payload.put("policy_inception_date", "2024-05-10");
        payload.put("prior_similar_claims", 2);
        payload.put("fraud_score", 0.85);

        // Mock Infra I/O: Redis Cache
        when(cacheService.get("Cache & Reference Data:cache:fraud_threshold")).thenReturn("0.80");
        when(cacheService.get("Cache & Reference Data:cache:inception_window_days")).thenReturn("7");

        // Mock Infra I/O: DynamoDB Data Store
        when(dataStoreService.getItem("Claims & Policy Data Store_table", "POL-SIU"))
                .thenReturn(Map.of("status", "ACTIVE", "inception_date", "2024-05-10"));

        // Act: Execute Validation
        ValidationResult result = claimInitiationService.validate(payload);

        // Assert: Expected Results
        assertNotNull(result, "Validation result should not be null");
        assertEquals("SIU_REFERRAL_CANDIDATE", result.getInitialClaimType(),
                "Initial claim type should be SIU referral candidate due to high fraud score and recent inception");
        assertEquals("INTAKE_REVIEW", result.getStatus(),
                "Status should be set to Intake Review for SIU candidates");

        // Verify: Side Effects
        verify(taskService).createTask("SIU Referral Review", "POL-SIU", "SIU_TRIGGER_REASON");
        verify(auditService).log(contains("SIU_TRIGGER_REASON"), any());
        
        // Verify Infra Calls
        verify(cacheService, times(2)).get(anyString());
        verify(dataStoreService).getItem("Claims & Policy Data Store_table", "POL-SIU");
    }
}
