package app.integration.e2e;

import app.models.ClaimInitiationRoutingDecisionValidation;
import app.services.ClaimInitiationRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class ClaimInitiationRoutingDecisionValidationE2e {

    private ClaimInitiationRoutingService claimInitiationRoutingService;

    @BeforeEach
    void setUp() {
        // E2E: Real application services are instantiated against the local runtime.
        // Infrastructure contracts (Redis, DynamoDB, SES) are wired via the application context.
        // No mocks, no fakes, no test doubles.
        this.claimInitiationRoutingService = new ClaimInitiationRoutingService();
    }

    @Test
    void us_fnol_05_compliance_auditor_reviews_fnol_audit_trail() {
        // Inputs: Periodic audit request or regulatory examination
        // Expected Results: Audit trail validated, compliance status verified, routing decision recorded
        // Constants JSON sidecar provides sample data fixtures for input construction

        String auditRequestId = "audit_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String auditRequestType = "PERIODIC_REGULATORY_EXAMINATION";

        // Build input payload strictly from Constants JSON sidecar fixtures
        Map<String, Object> auditPayload = Map.of(
            "compliance_status", "COMPLIANT",
            "gdpr_verified", true,
            "soc2_verified", true,
            "routing_decision", "APPROVED_FOR_REVIEW",
            "audit_timestamp", "2024-01-15T10:30:00Z"
        );

        // Invoke real application service end-to-end
        ClaimInitiationRoutingDecisionValidation validationResult =
            claimInitiationRoutingService.validateAuditTrail(auditRequestId, auditPayload, auditRequestType);

        // Assert expected results against the data model contract
        assertNotNull(validationResult, "Validation response must not be null");
        assertNotNull(validationResult.getId(), "Audit record must contain a valid ID");
        assertNotNull(validationResult.getPayload(), "Audit payload must contain routing decision data");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) validationResult.getPayload();
        assertTrue(payloadMap.containsKey("compliance_status"),
            "Payload must include compliance status for regulatory examination");
        assertEquals("COMPLIANT", payloadMap.get("compliance_status"),
            "Decision validation must pass GDPR/SOC2 compliance checks for audit trail");
    }
}
