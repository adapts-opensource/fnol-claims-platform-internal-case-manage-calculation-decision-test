package app.integration.e2e;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import app.models.Exposure;
import app.models.ReserveLine;
import app.services.InsuredEngagementTransformationService;
import app.services.RequestPayloadBuilderService;
import java.util.List;
import java.util.Map;

public class ConstructRequestPayloadsForInsuredEngagementTrackingDecisionTest {

    private InsuredEngagementTransformationService transformationService;
    private RequestPayloadBuilderService payloadBuilderService;

    @BeforeEach
    void setUp() {
        // Wire real application services end-to-end without mocks or fakes
        transformationService = new InsuredEngagementTransformationService();
        payloadBuilderService = new RequestPayloadBuilderService();
    }

    @Test
    void construct_request_payloads_for_insured_engagement_tracking_decision_transformation_without_mocks() {
        // Constants JSON sidecar fixture
        Map<String, Object> constants = Map.of("example_key", "example_value");

        // Build real model instances from test-case Inputs
        Exposure exposure = new Exposure("exp_001", "claim_001", "auto", 15000.00);
        ReserveLine reserveLine = new ReserveLine("res_001", exposure.getExposureId(), 15000.00, "USD", "Pending");

        // Construct request payloads using real services
        Map<String, Object> payload = payloadBuilderService.constructRequestPayloads(
                exposure,
                List.of(reserveLine),
                constants
        );

        // Verify transformation outcomes against expected results
        assertNotNull(payload, "Constructed payload must not be null");
        assertTrue(payload.containsKey("exposureId"), "Payload must contain exposureId");
        assertEquals("exp_001", payload.get("exposureId"));
        assertTrue(payload.containsKey("reserveLines"), "Payload must contain reserveLines");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reserveLines = (List<Map<String, Object>>) payload.get("reserveLines");
        assertEquals(1, reserveLines.size());

        Map<String, Object> reserveMap = reserveLines.get(0);
        assertEquals("res_001", reserveMap.get("reserveId"));
        assertEquals("Pending", reserveMap.get("approvalStatus"));
        assertEquals("USD", reserveMap.get("currency"));
        assertEquals(15000.00, (double) reserveMap.get("amount"), 0.001);
    }
}
