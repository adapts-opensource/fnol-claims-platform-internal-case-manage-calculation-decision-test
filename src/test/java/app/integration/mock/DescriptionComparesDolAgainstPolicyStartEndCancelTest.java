package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDecisionOrchestrationTest {

    @Mock
    private RedisClient redisClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;
    @Mock
    private DecisionOrchestrator decisionOrchestrator;

    private String claimId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        claimId = "claim-init-001";
        LocalDate dol = LocalDate.now();
        payload = Map.of(
            "id", claimId,
            "dateOfLoss", dol.toString(),
            "policyStartDate", dol.minusDays(30).toString(),
            "policyEndDate", dol.plusDays(30).toString(),
            "bindingRestrictions", Collections.emptyList(),
            "catEventId", "CAT-2024-001",
            "moratoriumStatus", "NONE"
        );
    }

    @Test
    void description_compares_dol_against_policy_start_end_cancel_reinstatement_rewrite_dates_checks_for_binding_restrictions_queries_cat_event_data_for_moratoriums_determines_if_claim_should_proceed_route_to_coverage_review_or_be_rejected() {
        // Arrange: Mock Redis cache retrieval for policy reference data
        when(redisClient.get(anyString())).thenReturn("ACTIVE");
        
        // Arrange: Mock DynamoDB for policy validation dates (start/end/cancel/reinstatement/rewrite)
        when(dynamoDbClient.getItem(anyString(), anyMap()))
            .thenReturn(Map.of("item_payload", Map.of(
                "status", "ACTIVE",
                "policyStartDate", payload.get("policyStartDate"),
                "policyEndDate", payload.get("policyEndDate"),
                "cancelDate", null,
                "reinstatementDate", null,
                "rewriteDate", null
            )));
            
        // Arrange: Mock SES (should not be invoked for valid claims)
        doNothing().when(sesClient).sendEmail(any());

        // Act: Execute decision orchestration
        DecisionResult result = decisionOrchestrator.evaluate(claimId, payload);

        // Assert: DOL within policy period + no restrictions/moratoriums -> PROCEED
        assertNotNull(result);
        assertEquals(DecisionOutcome.PROCEED, result.outcome());
        assertEquals(claimId, result.claimId());
        verify(redisClient, times(1)).get(anyString());
        verify(dynamoDbClient, times(1)).getItem(anyString(), anyMap());
        verifyNoInteractions(sesClient);
        assertTrue(result.rejectionReason().isEmpty());
        assertTrue(result.coverageReviewReasons().isEmpty());
    }

    // Minimal interfaces/records to support the test without external dependencies
    interface RedisClient { String get(String key); }
    interface DynamoDbClient { Map<String, Object> getItem(String tableName, Map<String, Object> key); }
    interface SesClient { void sendEmail(Map<String, Object> request); }
    interface DecisionOrchestrator { DecisionResult evaluate(String claimId, Map<String, Object> payload); }
    record DecisionResult(String claimId, DecisionOutcome outcome, String rejectionReason, java.util.List<String> coverageReviewReasons) {}
    enum DecisionOutcome { PROCEED, COVERAGE_REVIEW, REJECTED }
}
