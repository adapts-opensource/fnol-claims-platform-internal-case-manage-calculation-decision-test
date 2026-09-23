package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration mock test verifying that the decision transformation pipeline 
 * persists a complete audit trail including input/output contexts, metadata, and compliance flags.
 */
@ExtendWith(MockitoExtension.class)
public class AuditTrailRetainsFullDecisionContextTest {

    @Mock
    private DecisionTransformationService transformationService;

    @Mock
    private AuditTrailPersistence auditTrailPersistence;

    @Captor
    private ArgumentCaptor<AuditTrailEntry> auditTrailCaptor;

    private DecisionTransformationEngine transformationEngine;

    @BeforeEach
    void setUp() {
        transformationEngine = new DecisionTransformationEngine(transformationService, auditTrailPersistence);
    }

    @Test
    void audit_trail_retains_full_decision_context() {
        // Arrange
        String decisionId = "DEC-7890-ABCD";
        Instant executionTimestamp = Instant.now();
        Map<String, Object> inputPayload = Map.of(
                "claimId", "CLM-10293",
                "policyHolderId", "PH-8842",
                "riskFactor", "HIGH",
                "sourceSystem", "FNOL_MOBILE",
                "triggerRule", "AUTO_APPROVE_V2"
        );
        DecisionContext inputContext = new DecisionContext(decisionId, "PENDING", inputPayload);

        Map<String, Object> outputPayload = Map.of(
                "claimId", "CLM-10293",
                "policyHolderId", "PH-8842",
                "riskFactor", "HIGH",
                "decisionStatus", "APPROVED",
                "approvedBy", "RULE_ENGINE_V3",
                "complianceFlags", Collections.singletonList("GDPR_CONSENT_VERIFIED"),
                "transformationLatencyMs", 42
        );
        DecisionContext outputContext = new DecisionContext(decisionId, "APPROVED", outputPayload);

        when(transformationService.transform(any(DecisionContext.class))).thenReturn(outputContext);

        // Act
        transformationEngine.execute(inputContext, executionTimestamp);

        // Assert
        verify(auditTrailPersistence).persist(auditTrailCaptor.capture());
        AuditTrailEntry capturedEntry = auditTrailCaptor.getValue();

        assertNotNull(capturedEntry.getAuditId());
        assertEquals(decisionId, capturedEntry.getDecisionId());
        assertEquals("TRANSFORMATION", capturedEntry.getEventType());
        assertEquals(executionTimestamp, capturedEntry.getTimestamp());
        assertEquals("SYSTEM_RULE_ENGINE", capturedEntry.getActor());
        assertEquals(inputContext, capturedEntry.getInputContext());
        assertEquals(outputContext, capturedEntry.getOutputContext());
        assertEquals(inputPayload, capturedEntry.getInputPayload());
        assertEquals(outputPayload, capturedEntry.getOutputPayload());
        assertTrue(capturedEntry.getMetadata().containsKey("complianceFlags"));
        assertTrue(capturedEntry.getMetadata().containsKey("triggerRule"));
        assertFalse(capturedEntry.getMetadata().containsKey("pii_data"));
        assertFalse(capturedEntry.getMetadata().containsKey("socialSecurityNumber"));
    }

    // Minimal domain stubs to ensure standalone compilation for mock testing
    static class DecisionContext {
        private final String decisionId;
        private final String status;
        private final Map<String, Object> payload;

        DecisionContext(String decisionId, String status, Map<String, Object> payload) {
            this.decisionId = decisionId;
            this.status = status;
            this.payload = payload;
        }

        String getDecisionId() { return decisionId; }
        String getStatus() { return status; }
        Map<String, Object> getPayload() { return payload; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DecisionContext)) return false;
            DecisionContext that = (DecisionContext) o;
            return decisionId.equals(that.decisionId) && status.equals(that.status) && payload.equals(that.payload);
        }
    }

    interface DecisionTransformationService {
        DecisionContext transform(DecisionContext context);
    }

    interface AuditTrailPersistence {
        void persist(AuditTrailEntry entry);
    }

    static class AuditTrailEntry {
        private final String auditId = UUID.randomUUID().toString();
        private final String decisionId;
        private final String eventType;
        private final Instant timestamp;
        private final String actor;
        private final DecisionContext inputContext;
        private final DecisionContext outputContext;
        private final Map<String, Object> inputPayload;
        private final Map<String, Object> outputPayload;
        private final Map<String, Object> metadata;

        AuditTrailEntry(String decisionId, String eventType, Instant timestamp, String actor,
                        DecisionContext inputContext, DecisionContext outputContext,
                        Map<String, Object> inputPayload, Map<String, Object> outputPayload,
                        Map<String, Object> metadata) {
            this.decisionId = decisionId;
            this.eventType = eventType;
            this.timestamp = timestamp;
            this.actor = actor;
            this.inputContext = inputContext;
            this.outputContext = outputContext;
            this.inputPayload = inputPayload;
            this.outputPayload = outputPayload;
            this.metadata = metadata;
        }

        String getAuditId() { return auditId; }
        String getDecisionId() { return decisionId; }
        String getEventType() { return eventType; }
        Instant getTimestamp() { return timestamp; }
        String getActor() { return actor; }
        DecisionContext getInputContext() { return inputContext; }
        DecisionContext getOutputContext() { return outputContext; }
        Map<String, Object> getInputPayload() { return inputPayload; }
        Map<String, Object> getOutputPayload() { return outputPayload; }
        Map<String, Object> getMetadata() { return metadata; }
    }

    static class DecisionTransformationEngine {
        private final DecisionTransformationService service;
        private final AuditTrailPersistence persistence;

        DecisionTransformationEngine(DecisionTransformationService service, AuditTrailPersistence persistence) {
            this.service = service;
            this.persistence = persistence;
        }

        void execute(DecisionContext context, Instant timestamp) {
            DecisionContext transformed = service.transform(context);
            Map<String, Object> metadata = Map.of("source", "INSURED_ENGAGEMENT_TRACKING", "version", "1.0.0");
            AuditTrailEntry entry = new AuditTrailEntry(
                    context.getDecisionId(), "TRANSFORMATION", timestamp, "SYSTEM_RULE_ENGINE",
                    context, transformed, context.getPayload(), transformed.getPayload(), metadata
            );
            persistence.persist(entry);
        }
    }
}
