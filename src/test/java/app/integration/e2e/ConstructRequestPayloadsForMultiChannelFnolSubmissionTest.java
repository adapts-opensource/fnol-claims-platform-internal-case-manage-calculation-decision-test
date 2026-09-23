package app.integration.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

// Application stubs are expected under src/main/java/app/
// import app.models.Claim;
// import app.models.ClaimDecision;
// import app.models.FnolSubmissionRequest;
// import app.services.FnolValidationService;

public class MultiChannelFnolSubmissionValidationDecisionTest {

    // Inputs and expected results sourced from Constants JSON sidecar
    private static final String TENANT_ID = "newco-insurance-tenant-001";
    private static final String POLICY_ID = "POL-987654321";
    private static final String EXPECTED_DECISION_STATUS = "ACCEPTED";
    private static final String CLAIM_ID_PREFIX = "CLM-";

    @BeforeEach
    void setUp() {
        // Initialize shared test context or structured logging hooks if required
    }

    @Test
    void constructRequestPayloadsForMultiChannelFnolSubmissionValidationDecisionWithoutMocks() {
        List<String> channels = List.of("WEB", "MOBILE", "AGENT");
        
        List<FnolSubmissionRequest> payloads = channels.stream()
                .map(channel -> new FnolSubmissionRequest(
                        channel,
                        TENANT_ID,
                        POLICY_ID,
                        "idemp-key-" + channel.toLowerCase(),
                        "2024-10-01T10:00:00Z",
                        "Sample FNOL description for " + channel
                ))
                .toList();

        for (var payload : payloads) {
            assertDoesNotThrow(() -> {
                ClaimDecision decision = FnolValidationService.validateAndDecide(payload);
                assertNotNull(decision, "Decision must not be null for valid multi-channel payload");
                assertEquals(EXPECTED_DECISION_STATUS, decision.status(), "Payload should pass validation and be accepted");
                assertNotNull(decision.claim(), "Claim entity must be generated upon acceptance");
                assertEquals(TENANT_ID, decision.claim().tenantId(), "Tenant ID must match input boundary validation");
                assertTrue(decision.claim().claimId().startsWith(CLAIM_ID_PREFIX), "Claim ID must follow statutory naming convention");
            }, "Validation service must process " + payload.channel() + " channel payload without throwing");
        }
    }
}
