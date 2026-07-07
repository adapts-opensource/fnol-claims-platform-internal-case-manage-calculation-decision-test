package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:state_transition:orchestration.
 * Validates merge decision logic, infrastructure contract mocking, and NFR compliance.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchMockTest {

    @Mock
    private ClaimDataStoreMock claimDataStore;
    @Mock
    private RulesTriageServiceMock rulesTriageService;
    @Mock
    private DocumentManagementMock documentManagement;
    @Mock
    private ClaimDataStandardizationOrchestrator orchestrator;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = new HashMap<>();
        testPayload.put("id", "orch-001");
        testPayload.put("policyId", "POL-8842");
        testPayload.put("dateOfLoss", "2023-11-15");
        testPayload.put("cause", "Collision");
        testPayload.put("claimIds", Map.of("primary", "CLM-A", "duplicate", "CLM-B"));
        testPayload.put("metadata", Map.of("tlsEnabled", true, "piiMasked", true));
    }

    @Test
    void decision_merge_valid_rule_same_policy_dol_cause_allow_merge_expected_outcome_link_exposures_close_duplicate() {
        // Arrange: Input validation & least_privilege_iam simulation
        assertNotNull(testPayload.get("id"), "Orchestration ID must be present");
        assertTrue((Boolean) testPayload.get("metadata").get("tlsEnabled"), "TLS in transit must be enforced");
        assertTrue((Boolean) testPayload.get("metadata").get("piiMasked"), "PII masking required for compliance");

        // Mock: Rules & Triage Service evaluates merge rule
        when(rulesTriageService.evaluateMergeRule(anyString(), anyString(), anyString()))
                .thenReturn("MERGE_ALLOWED");

        // Mock: Claim Data Store returns current state for partition key
        when(claimDataStore.getItemByPartitionKey(eq("pk"), anyString()))
                .thenReturn(new HashMap<>());

        // Mock: S3 Document Management returns object URI after write
        when(documentManagement.writeObject(anyString(), anyString()))
                .thenReturn("s3://Document Management-bucket/Document Management/CLM-A.json");

        // Arrange: Expected state transition payload
        Map<String, Object> expectedState = new HashMap<>();
        expectedState.put("decision", "Merge valid?");
        expectedState.put("rule", "Same policy + DoL + cause -> allow merge");
        expectedState.put("outcome", "Link exposures, close duplicate");
        expectedState.put("linkExposures", true);
        expectedState.put("closeDuplicate", true);
        expectedState.put("status", "MERGED");

        // Act: Orchestrate state transition
        Map<String, Object> result = orchestrator.transitionState(testPayload, claimDataStore, rulesTriageService, documentManagement);

        // Assert: Decision & outcome
        assertEquals("Merge valid?", result.get("decision"), "Decision key must match");
        assertEquals("Link exposures, close duplicate", result.get("outcome"), "Expected outcome must match rule");
        assertTrue((Boolean) result.get("linkExposures"), "Exposures must be linked");
        assertTrue((Boolean) result.get("closeDuplicate"), "Duplicate claim must be closed");
        assertEquals("MERGED", result.get("status"), "State must transition to MERGED");

        // Assert: Infrastructure I/O contract validation
        verify(rulesTriageService, times(1)).evaluateMergeRule(eq("POL-8842"), eq("2023-11-15"), eq("Collision"));
        verify(claimDataStore, times(1)).updateItem(eq("pk"), eq("CLM-A"), anyMap());
        verify(claimDataStore, times(1)).updateItem(eq("pk"), eq("CLM-B"), anyMap());
        verify(documentManagement, times(1)).writeObject(eq("Document Management-bucket"), eq("Document Management/CLM-A.json"));

        // Assert: Observability & Security NFRs
        assertNotNull(result.get("structuredTraceId"), "Structured logging requires trace correlation");
        assertFalse(result.containsKey("rawPii"), "Input validation must strip raw PII from outputs");
    }

    // Minimal mock interfaces representing infra contracts
    interface ClaimDataStoreMock {
        Map<String, Object> getItemByPartitionKey(String pk, String pkValue);
        void updateItem(String pk, String pkValue, Map<String, Object> item);
    }

    interface RulesTriageServiceMock {
        String evaluateMergeRule(String policyId, String dateOfLoss, String cause);
    }

    interface DocumentManagementMock {
        String writeObject(String bucketName, String objectKeyPattern);
    }

    interface ClaimDataStandardizationOrchestrator {
        Map<String, Object> transitionState(Map<String, Object> payload,
                                            ClaimDataStoreMock claimDataStore,
                                            RulesTriageServiceMock rulesTriageService,
                                            DocumentManagementMock documentManagement);
    }
}
