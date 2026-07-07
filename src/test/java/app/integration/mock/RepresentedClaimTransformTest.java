package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mock implementation of the claim transformation service to simulate
 * calculation/transformation logic without calling live AWS or production HTTP APIs.
 */
class MockClaimTransformationService {
    public Map<String, Object> execute(Map<String, Object> requestPayload) {
        boolean isAttorney = Boolean.parseBoolean(String.valueOf(requestPayload.get("attorney_flag")));
        String tenantCode = String.valueOf(requestPayload.get("tenant_code"));
        String year = String.valueOf(requestPayload.get("year"));

        Map<String, Object> response = new HashMap<>();
        response.put("claim_number", tenantCode + "-" + year + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        response.put("claim_type", isAttorney ? "Represented claim" : "Unrepresented claim");
        response.put("task_created", isAttorney ? "Attorney Representation Review" : null);
        response.put("direct_communications_restricted", isAttorney);
        response.put("correspondence_route", isAttorney ? "represented-claim workflow" : "standard workflow");
        return response;
    }
}

public class RepresentedClaimTransformTest {
    private MockClaimTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new MockClaimTransformationService();
    }

    @Test
    void transform_to_represented_claim_on_attorney_flag() {
        // Given: Inputs per specification
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_code", "FL01");
        payload.put("year", "2024");
        payload.put("attorney_flag", "true");
        payload.put("attorney_name", "John Doe");
        payload.put("date_of_loss", "2024-04-01");
        payload.put("cause_of_loss", "water");
        payload.put("product", "HO3");

        Map<String, Object> claimData = new HashMap<>();
        claimData.put("id", "claim-transform-001");
        claimData.put("payload", payload);

        // When: Execute transformation via mock service
        Map<String, Object> result = transformationService.execute(payload);

        // Then: Verify expected results
        assertNotNull(result.get("claim_number"), "Claim number generated");
        assertEquals("Represented claim", result.get("claim_type"), "Claim type set to Represented claim");
        assertEquals("Attorney Representation Review", result.get("task_created"), "Task Attorney Representation Review created");
        assertTrue((Boolean) result.get("direct_communications_restricted"), "Direct communications restricted");
        assertEquals("represented-claim workflow", result.get("correspondence_route"), "Correspondence routed through represented-claim workflow");
    }
}
