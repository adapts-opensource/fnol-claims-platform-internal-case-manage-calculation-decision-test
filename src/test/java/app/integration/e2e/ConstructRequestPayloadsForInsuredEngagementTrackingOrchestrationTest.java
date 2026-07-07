package app.integration.e2e;

import app.models.Claim;
import app.models.Exposure;
import app.models.ReserveLine;
import app.services.InsuredEngagementService;
import app.services.OrchestrationDecisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;

public class ConstructRequestPayloadsForInsuredEngagementTrackingOrchestrationDecisionWithoutMocksTest {

    private OrchestrationDecisionService orchestrationService;
    private InsuredEngagementService engagementService;
    private Map<String, Object> constantsFixture;

    @BeforeEach
    void setUp() {
        constantsFixture = parseConstantsFixture();
        // E2E: Instantiate real application services without mocks
        orchestrationService = new OrchestrationDecisionService();
        engagementService = new InsuredEngagementService();
    }

    private Map<String, Object> parseConstantsFixture() {
        String constantsJson = """
            {
              "claim_id": "CLM-1001",
              "exposure_id": "EXP-2001",
              "incident_type": "collision",
              "reserve_line_reserve_id": "RES-3001",
              "reserve_amount": "5000.00",
              "reserve_currency": "USD",
              "reserve_status": "Pending",
              "insured_email": "insured@example.com",
              "ses_region": "us-east-1",
              "dynamodb_table": "NewCo_Claims_Data",
              "s3_bucket": "newco-document-store"
            }
            """;
        try {
            return new ObjectMapper().readValue(constantsJson, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse constants fixture", e);
        }
    }

    @Test
    void construct_request_payloads_for_insured_engagement_tracking_orchestration_decision_without_mocks() {
        // 1. Extract Inputs from Constants JSON sidecar
        String claimId = (String) constantsFixture.get("claim_id");
        String exposureId = (String) constantsFixture.get("exposure_id");
        String reserveId = (String) constantsFixture.get("reserve_line_reserve_id");
        BigDecimal amount = new BigDecimal((String) constantsFixture.get("reserve_amount"));
        String currency = (String) constantsFixture.get("reserve_currency");
        String status = (String) constantsFixture.get("reserve_status");
        String insuredEmail = (String) constantsFixture.get("insured_email");

        // 2. Build Domain Models (Real objects, no stubs)
        ReserveLine reserveLine = new ReserveLine(reserveId, exposureId, amount, currency, status);
        Exposure exposure = new Exposure(exposureId, claimId, (String) constantsFixture.get("incident_type"));
        Claim claim = new Claim(claimId, exposureId, insuredEmail);

        // 3. Construct Request Payload for Orchestration:Decision
        Map<String, Object> decisionPayload = new HashMap<>();
        decisionPayload.put("claim", claim);
        decisionPayload.put("exposure", exposure);
        decisionPayload.put("reserveLine", reserveLine);
        decisionPayload.put("insuredContact", insuredEmail);
        decisionPayload.put("sesRegion", constantsFixture.get("ses_region"));
        decisionPayload.put("dynamoTable", constantsFixture.get("dynamodb_table"));
        decisionPayload.put("s3Bucket", constantsFixture.get("s3_bucket"));

        // 4. Input Validation NFR: Assert payload structure before invocation
        assertNotNull(decisionPayload.get("claim"), "Claim must be present in payload");
        assertNotNull(decisionPayload.get("reserveLine"), "ReserveLine must be present in payload");
        assertEquals(status, reserveLine.getApprovalStatus(), "Approval status must match input constants");

        // 5. Invoke Real Orchestration Service (E2E: no mocks, no live AWS calls)
        Map<String, Object> decisionOutcome = orchestrationService.processDecision(decisionPayload);

        // 6. Assert Expected Results
        assertNotNull(decisionOutcome, "Decision outcome must not be null");
        assertEquals("DECISION_COMPLETED", decisionOutcome.get("status"), "Orchestration stage must complete");
        assertNotNull(decisionOutcome.get("engagementTrackingId"), "Engagement tracking ID must be generated");
        assertNotNull(decisionOutcome.get("auditLog"), "Structured logging/audit must be recorded");

        // 7. Verify Insured Engagement Tracking Integration
        Map<String, Object> engagementResult = engagementService.trackInsuredEngagement(
                decisionOutcome.get("engagementTrackingId").toString(), insuredEmail);
        assertNotNull(engagementResult.get("messageId"), "SES message ID must be returned for insured engagement");
    }
}
