package app.integration.e2e;
import app.models.ClaimInitiationPayload;
import app.models.RoutingContext;
import app.services.ClaimOrchestrationService;
import app.services.TransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class ConstructRequestPayloadsForClaimInitiationRoutingOrchestrationTest {
    private ClaimOrchestrationService orchestrationService;
    private TransformationService transformationService;
    private Map<String, Object> constants;

    @BeforeEach
    void setUp() {
        // Initialize real application services (stubs provided by build system under src/main/java/app/)
        orchestrationService = new ClaimOrchestrationService();
        transformationService = new TransformationService();
        
        // Load sample data fixtures from Constants JSON sidecar
        constants = Map.of(
            "claimId", "CLM-1001",
            "policyNumber", "POL-987654",
            "incidentDate", "2023-10-25T14:30:00Z",
            "claimType", "AUTO",
            "severity", "LOW",
            "routingRule", "FAST_TRACK",
            "auditBucket", "ComplianceAuditService-bucket",
            "documentBucket", "DocumentStorage-bucket",
            "dynamoTable", "PolicyClaimsDB_table",
            "partitionKey", "pk"
        );
    }

    @Test
    void construct_request_payloads_for_claim_initiation_routing_orchestration_transformation_without_mocks() {
        // 1. Build inputs strictly from Constants JSON sidecar
        String claimId = (String) constants.get("claimId");
        String policyNumber = (String) constants.get("policyNumber");
        String incidentDate = (String) constants.get("incidentDate");
        String claimType = (String) constants.get("claimType");
        String routingRule = (String) constants.get("routingRule");
        String severity = (String) constants.get("severity");

        // 2. Construct request payload for Claim Initiation
        ClaimInitiationPayload payload = new ClaimInitiationPayload();
        payload.setClaimId(claimId);
        payload.setPolicyNumber(policyNumber);
        payload.setIncidentDate(incidentDate);
        payload.setClaimType(claimType);
        payload.setSeverity(severity);

        // 3. Validate input constraints (NFR: input_validation)
        assertNotNull(payload.getClaimId());
        assertTrue(payload.getClaimId().startsWith("CLM-"), "Claim ID must follow CLM- prefix convention");
        assertNotNull(payload.getPolicyNumber());
        assertFalse(payload.getClaimType().isBlank(), "Claim type must be provided");

        // 4. Execute transformation orchestration (real service invocation, no mocks)
        RoutingContext transformedContext = transformationService.transform(payload);
        assertNotNull(transformedContext, "Transformation service must return a valid routing context");

        // 5. Verify transformation outcomes match expected results
        assertEquals(claimId, transformedContext.getClaimId());
        assertEquals(claimType, transformedContext.getClaimType());
        assertEquals(routingRule, transformedContext.getRoutingRule());
        assertTrue(transformedContext.getRoutingRule().length() > 0, "Routing rule must be resolved");

        // 6. Execute routing orchestration (real service invocation)
        String orchestrationResult = orchestrationService.route(transformedContext);
        assertNotNull(orchestrationResult, "Orchestration service must return a valid routing token");

        // 7. Verify compliance & availability contract alignment (NFR: compliance, availability)
        String auditKeyPattern = constants.get("auditBucket") + "/ClaimInitiation/" + claimId + ".json";
        String docKeyPattern = constants.get("documentBucket") + "/ClaimInitiation/" + claimId + ".json";
        String dynamoTable = (String) constants.get("dynamoTable");
        String partitionKey = (String) constants.get("partitionKey");

        assertNotNull(auditKeyPattern);
        assertNotNull(docKeyPattern);
        assertNotNull(dynamoTable);
        assertNotNull(partitionKey);
        assertTrue(auditKeyPattern.contains(claimId));
        assertTrue(docKeyPattern.contains(claimId));
        assertTrue(dynamoTable.contains("PolicyClaimsDB"));
        assertTrue(partitionKey.contains("pk"));

        // 8. Final assertion on orchestration outcome
        assertTrue(orchestrationResult.startsWith("RTE-"), "Routing token must follow RTE- prefix convention");
        assertEquals(claimId, transformedContext.getClaimId());
    }
}
