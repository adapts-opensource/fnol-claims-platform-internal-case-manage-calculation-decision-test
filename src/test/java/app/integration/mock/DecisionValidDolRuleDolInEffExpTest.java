package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @InjectMocks
    private ClaimRoutingDecisionOrchestrator claimRoutingDecisionOrchestrator;

    @BeforeEach
    void setUp() {
        // Ensure test isolation and thread-safety compliance per NFR
        reset(redisCacheService, dynamoDbService);
    }

    @Test
    void decision_valid_dol_rule_dol_in_eff_exp_and_no_restrictions_expected_outcome_dol_valid_proceed_to_duplicate_check() {
        // Given: DOL within [eff, exp], no restrictions
        String claimId = "CLM-12345-VALID-DOL";
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 15);
        LocalDate effDate = LocalDate.of(2023, 1, 1);
        LocalDate expDate = LocalDate.of(2024, 12, 31);

        Map<String, Object> payload = Map.of(
                "id", claimId,
                "dateOfLoss", dateOfLoss.toString(),
                "policyEffectiveDate", effDate.toString(),
                "policyExpirationDate", expDate.toString(),
                "restrictions", Map.of("active", false, "type", "NONE")
        );

        // Mock Redis cache miss & DynamoDB lookup
        when(redisCacheService.getCacheEntry(eq("Cache & Reference Data:cache:" + claimId)))
                .thenReturn(null);
        when(dynamoDbService.getItem(eq("Claims & Policy Data Store_table"), eq("pk"), eq(claimId)))
                .thenReturn(Map.of("status", "INITIATED", "policyId", "POL-987"));

        // When
        Map<String, Object> result = claimRoutingDecisionOrchestrator.evaluateDecision(payload);

        // Then
        assertNotNull(result, "Decision result should not be null");
        assertEquals("VALID_DOL", result.get("decision"), "Decision should be Valid DOL");
        assertEquals("PROCEED_TO_DUPLICATE_CHECK", result.get("nextStep"), "Should proceed to duplicate check");
        assertFalse((Boolean) result.get("hasRestrictions"), "No restrictions should be present");

        // Verify infra interactions & structured logging placeholders
        verify(redisCacheService).putCacheEntry(eq("Cache & Reference Data:cache:" + claimId), anyString(), eq(3600));
        verify(dynamoDbService).putItem(eq("Claims & Policy Data Store_table"), anyMap());
        // Note: Structured logging, TLS, and IAM validations are handled by framework/config in production
    }
}

// Minimal mock interfaces for standalone compilation
interface RedisCacheService {
    String getCacheEntry(String key);
    void putCacheEntry(String key, String value, int ttlSeconds);
}

interface DynamoDbService {
    Map<String, Object> getItem(String tableName, String pk, String pkValue);
    void putItem(String tableName, Map<String, Object> item);
}

interface ClaimRoutingDecisionOrchestrator {
    Map<String, Object> evaluateDecision(Map<String, Object> payload);
}
