package app.integration.e2e;

import app.models.ClaimDataStandardizationTransformationValida;
import app.services.ClaimDataStandardizationValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class ConstructRequestPayloadsForClaimDataStandardizationValidationDecisionWithoutMocks {

    private ClaimDataStandardizationValidationService validationService;

    // Fixture values sourced from Constants JSON sidecar
    private static final String TEST_ENTITY_ID = "claim-data-std-val-001";
    private static final String PAYLOAD_POLICY_ID = "POL-NEWCO-2024-001";
    private static final String PAYLOAD_CLAIM_TYPE = "property";
    private static final String PAYLOAD_STATUS = "submitted";
    private static final String EXPECTED_DECISION_KEY = "decision";

    @BeforeEach
    void setUp() {
        // Wire real application service (no mocks, no stubs, no patches)
        this.validationService = new ClaimDataStandardizationValidationService();
    }

    @Test
    void constructRequestPayloadsForClaimDataStandardizationValidationDecisionWithoutMocks() {
        // Construct request payload strictly from test-case Inputs / Expected Results and Constants JSON sidecar
        Map<String, Object> requestPayload = Map.of(
                "policyId", PAYLOAD_POLICY_ID,
                "claimType", PAYLOAD_CLAIM_TYPE,
                "status", PAYLOAD_STATUS
        );

        // Map to typed model entity
        ClaimDataStandardizationTransformationValida entity = new ClaimDataStandardizationTransformationValida();
        entity.setId(TEST_ENTITY_ID);
        entity.setPayload(requestPayload);

        // Invoke real E2E service pipeline
        ClaimDataStandardizationTransformationValida result = validationService.processValidationDecision(entity);

        // Assert real service outcome
        assertNotNull(result, "Real service must return a non-null validation decision result");
        assertEquals(TEST_ENTITY_ID, result.getId(), "Entity ID must be preserved through the pipeline");
        assertNotNull(result.getPayload(), "Result payload must not be null after transformation");
        assertTrue(result.getPayload().containsKey(EXPECTED_DECISION_KEY),
                "Decision payload must contain the 'decision' key after standardization validation");
    }
}
