package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditCapturesOverrideReasonAndPolicySnapshotTest {

    @Mock
    private PolicyCoverageValidator policyValidator;

    @Mock
    private DocumentMediaStore documentStore;

    @Mock
    private CommunicationAckManager communicationManager;

    @Mock
    private AuditRecordService auditService;

    private FnolDecisionValidationService fnolService;

    @Captor
    private ArgumentCaptor<String> idCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @BeforeEach
    void setUp() {
        fnolService = new FnolDecisionValidationService(
                policyValidator, documentStore, communicationManager, auditService
        );
    }

    @Test
    void audit_captures_override_reason_and_policy_snapshot() {
        // Given
        String submissionId = "fnol-sub-001";
        String overrideReason = "Manual override due to system latency exceeding SLA";
        Map<String, Object> policySnapshot = Map.of(
                "policyId", "POL-98765",
                "coverageType", "AUTO_COMPREHENSIVE",
                "status", "ACTIVE",
                "effectiveDate", "2024-01-01"
        );
        Map<String, Object> inputPayload = Map.of(
                "override_reason", overrideReason,
                "policy_snapshot", policySnapshot
        );

        when(policyValidator.validatePolicy("POL-98765")).thenReturn(policySnapshot);

        // When
        fnolService.processSubmission(submissionId, inputPayload);

        // Then
        verify(auditService, times(1)).captureAudit(idCaptor.capture(), payloadCaptor.capture());

        assertEquals(submissionId, idCaptor.getValue());

        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals(overrideReason, capturedPayload.get("override_reason"));
        assertSame(policySnapshot, capturedPayload.get("policy_snapshot"));
        assertNotNull(capturedPayload.get("timestamp"));
        assertEquals("VALIDATION_COMPLETE", capturedPayload.get("decision_status"));
    }

    // Infrastructure Contract Interfaces (Mocked)
    interface PolicyCoverageValidator {
        Map<String, Object> validatePolicy(String policyId);
    }

    interface DocumentMediaStore {
        String storeDocument(String bucketName, String objectKeyPattern, Map<String, Object> payload);
    }

    interface CommunicationAckManager {
        String sendNotification(String fromAddress, List<String> toAddresses, String region);
    }

    interface AuditRecordService {
        void captureAudit(String id, Map<String, Object> payload);
    }

    // Service Under Test
    static class FnolDecisionValidationService {
        private final PolicyCoverageValidator policyValidator;
        private final DocumentMediaStore documentStore;
        private final CommunicationAckManager communicationManager;
        private final AuditRecordService auditService;

        FnolDecisionValidationService(PolicyCoverageValidator policyValidator,
                                      DocumentMediaStore documentStore,
                                      CommunicationAckManager communicationManager,
                                      AuditRecordService auditService) {
            this.policyValidator = policyValidator;
            this.documentStore = documentStore;
            this.communicationManager = communicationManager;
            this.auditService = auditService;
        }

        void processSubmission(String id, Map<String, Object> payload) {
            Map<String, Object> policySnapshot = (Map<String, Object>) payload.get("policy_snapshot");
            if (policySnapshot != null && policySnapshot.containsKey("policyId")) {
                policyValidator.validatePolicy((String) policySnapshot.get("policyId"));
            }

            Map<String, Object> auditPayload = new HashMap<>(payload);
            auditPayload.put("timestamp", Instant.now().toString());
            auditPayload.put("decision_status", "VALIDATION_COMPLETE");

            documentStore.storeDocument("Document_Media_Store-bucket",
                    "Document_Media_Store/" + id + ".json", auditPayload);
            communicationManager.sendNotification("noreply@newco.insurance",
                    List.of("claims@newco.insurance"), "us-east-1");

            auditService.captureAudit(id, auditPayload);
        }
    }
}
