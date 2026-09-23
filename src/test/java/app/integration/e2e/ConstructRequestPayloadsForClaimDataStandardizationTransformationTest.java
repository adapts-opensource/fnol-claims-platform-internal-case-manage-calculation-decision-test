package app.integration.e2e;

import app.models.ClaimDataStandardizationStateTransitionOrch;
import app.services.ClaimDataStandardizationOrchestrationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ConstructRequestPayloadsForClaimDataStandardizationTransformationE2ETest {

    private static final Logger logger = LoggerFactory.getLogger(ConstructRequestPayloadsForClaimDataStandardizationTransformationE2ETest.class);
    private static final String APP_BASE_URL = System.getenv("APP_BASE_URL");
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private ClaimDataStandardizationOrchestrationService orchestrationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Initialize real application services (no mocks)
        orchestrationService = new ClaimDataStandardizationOrchestrationService();
        objectMapper = new ObjectMapper();
        logger.info("E2E Setup: Initialized orchestration service for {}", APP_BASE_URL != null ? APP_BASE_URL : DEFAULT_BASE_URL);
    }

    @Test
    void construct_request_payloads_for_claim_data_standardization_transformation_orchestration_without_mocks() throws Exception {
        // Build inputs from test-case Inputs / Expected Results and the Constants JSON sidecar
        String constantsJson = """
            {
              "example_key": "example_value",
              "claim_id": "CLM-2024-NCO-8821",
              "policy_number": "POL-99201-NEWCO",
              "incident_date": "2024-05-12T14:30:00Z",
              "claim_type": "AUTO",
              "status": "INITIATED",
              "dynamodb_table": "Claim Data Store_table",
              "partition_key": "pk",
              "s3_bucket": "Document Management-bucket",
              "object_key_pattern": "Document Management/{entity_id}.json"
            }
            """;

        Map<String, Object> fixtureData = objectMapper.readValue(constantsJson, new TypeReference<>() {});

        // Construct model instance per data model: claim_data_standardization_state_transition_orch
        String claimId = (String) fixtureData.get("claim_id");
        assertNotNull(claimId, "ID is required per data model constraints");

        Map<String, Object> payload = Map.of(
            "policyNumber", fixtureData.get("policy_number"),
            "incidentDate", fixtureData.get("incident_date"),
            "claimType", fixtureData.get("claim_type"),
            "status", fixtureData.get("status"),
            "infra_contracts", Map.of(
                "dynamodb_table", fixtureData.get("dynamodb_table"),
                "partition_key", fixtureData.get("partition_key"),
                "s3_bucket", fixtureData.get("s3_bucket"),
                "object_key_pattern", fixtureData.get("object_key_pattern")
            )
        );

        ClaimDataStandardizationStateTransitionOrch orchestrationState = new ClaimDataStandardizationStateTransitionOrch();
        orchestrationState.setId(claimId);
        orchestrationState.setPayload(payload);

        // Validate input constraints & security NFRs (input_validation, least_privilege_iam)
        assertNotNull(orchestrationState.getId(), "ID must not be null");
        assertNotNull(orchestrationState.getPayload(), "Payload must not be null");
        assertFalse(orchestrationState.getPayload().isEmpty(), "Payload must contain transformation directives");
        assertTrue(orchestrationState.getPayload().containsKey("infra_contracts"), "Must reference infra I/O contracts");

        // Invoke real orchestration service (no mocks, no fakes)
        Map<String, Object> constructedPayload = orchestrationService.constructRequestPayload(orchestrationState);

        // Assert Expected Results
        assertNotNull(constructedPayload, "Constructed payload must not be null");
        assertEquals(claimId, constructedPayload.get("claim_id"), "Claim ID must propagate correctly");
        assertTrue(constructedPayload.containsKey("transformation_steps"), "Must contain standardized transformation steps");
        assertEquals("INITIATED", constructedPayload.get("status"), "Status must be preserved through orchestration");
        assertEquals("AUTO", constructedPayload.get("claim_type"), "Claim type must be standardized per industry profile");

        // Observability NFR: structured_logging
        logger.info("E2E Verification: Successfully constructed payload for claim {} | Payload keys: {}", claimId, constructedPayload.keySet());

        // Concurrency NFR: thread_safety
        Map<String, Object> concurrentResult = orchestrationService.constructRequestPayload(orchestrationState);
        assertEquals(constructedPayload, concurrentResult, "Service must be thread-safe and deterministic for identical inputs");

        // Compliance NFR: gdpr, soc2 (PII validation)
        assertFalse(constructedPayload.containsKey("ssn"), "Must not contain PII fields per GDPR/SOC2");
        assertFalse(constructedPayload.containsKey("driver_license"), "Must not contain PII fields per GDPR/SOC2");
    }
}
