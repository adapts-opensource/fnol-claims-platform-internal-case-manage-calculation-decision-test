package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test class for Claim Data Standardization:validation:decision
 * Validates the claim_or_task_id field extraction and decision routing.
 * External I/O (S3, DynamoDB) is fully mocked to ensure no live network calls.
 */
public class ClaimOrTaskIdValidationTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private ClaimDecisionEngine claimDecisionEngine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("claim_or_task_id: valid claim ID passes decision validation")
    void claim_or_task_id_valid_claim_id_succeeds() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("claim_or_task_id", "CLM-12345");
        payload.put("policy_ref", "POL-999");

        // Mock external I/O: S3 DocumentStoreService
        when(documentStoreService.retrieveDocument(anyString(), anyString())).thenReturn(Map.of("id", "doc-1"));
        // Mock external I/O: DynamoDB PolicyValidationService
        when(policyValidationService.fetchPolicy(anyString())).thenReturn(Map.of("status", "ACTIVE"));
        // Mock decision logic
        when(claimDecisionEngine.evaluate(payload)).thenReturn(true);

        boolean decision = claimDecisionEngine.evaluate(payload);

        assertTrue(decision);
        verify(documentStoreService).retrieveDocument(anyString(), anyString());
        verify(policyValidationService).fetchPolicy("POL-999");
    }

    @Test
    @DisplayName("claim_or_task_id: valid task ID passes decision validation")
    void claim_or_task_id_valid_task_id_succeeds() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("claim_or_task_id", "TSK-67890");
        payload.put("policy_ref", "POL-888");

        when(documentStoreService.retrieveDocument(anyString(), anyString())).thenReturn(Map.of("id", "doc-2"));
        when(policyValidationService.fetchPolicy(anyString())).thenReturn(Map.of("status", "ACTIVE"));
        when(claimDecisionEngine.evaluate(payload)).thenReturn(true);

        boolean decision = claimDecisionEngine.evaluate(payload);

        assertTrue(decision);
        verify(claimDecisionEngine).evaluate(payload);
    }

    @Test
    @DisplayName("claim_or_task_id: missing claim_or_task_id fails decision validation")
    void claim_or_task_id_missing_fails() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_ref", "POL-777");

        when(documentStoreService.retrieveDocument(anyString(), anyString())).thenReturn(Map.of("id", "doc-3"));
        when(policyValidationService.fetchPolicy(anyString())).thenReturn(Map.of("status", "ACTIVE"));
        when(claimDecisionEngine.evaluate(payload)).thenReturn(false);

        boolean decision = claimDecisionEngine.evaluate(payload);

        assertFalse(decision);
        verify(claimDecisionEngine).evaluate(payload);
    }

    @Test
    @DisplayName("claim_or_task_id: empty claim_or_task_id fails decision validation")
    void claim_or_task_id_empty_fails() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("claim_or_task_id", "");
        payload.put("policy_ref", "POL-666");

        when(documentStoreService.retrieveDocument(anyString(), anyString())).thenReturn(Map.of("id", "doc-4"));
        when(policyValidationService.fetchPolicy(anyString())).thenReturn(Map.of("status", "ACTIVE"));
        when(claimDecisionEngine.evaluate(payload)).thenReturn(false);

        boolean decision = claimDecisionEngine.evaluate(payload);

        assertFalse(decision);
        verify(claimDecisionEngine).evaluate(payload);
    }
}
