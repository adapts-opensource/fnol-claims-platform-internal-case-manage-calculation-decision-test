package app.integration.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import app.models.ClaimPayload;
import app.models.ClaimResult;
import app.models.TaskRecord;
import app.models.DiaryEvent;
import app.models.AuditLogEntry;
import app.services.ClaimTransformationService;
import app.services.ClaimRoutingService;
import app.services.TaskGenerationService;
import app.services.DiaryEventService;
import app.services.AuditLogService;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class AttorneyRepFlagRoutingAndTaskGenTest {

    private ClaimTransformationService transformationService;
    private ClaimRoutingService routingService;
    private TaskGenerationService taskGenerationService;
    private DiaryEventService diaryEventService;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        // Initialize real application services (no mocks/fakes)
        transformationService = new ClaimTransformationService();
        routingService = new ClaimRoutingService();
        taskGenerationService = new TaskGenerationService();
        diaryEventService = new DiaryEventService();
        auditLogService = new AuditLogService();
    }

    @Test
    void route_attorney_represented_claim_and_generate_tasks() {
        // Build inputs from test-case Inputs and Constants JSON sidecar
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_code", "FL01");
        payload.put("policy_number", "DP3-2024-5544");
        payload.put("risk_address", "789 Maple Dr, Miami FL");
        payload.put("date_of_loss", "2024-10-05");
        payload.put("cause_of_loss", "fire");
        payload.put("product_form", "DP3");
        payload.put("reporter_type", "attorney");
        payload.put("attorney_flag", true);
        payload.put("letter_of_representation_url", "s3://docs/lor.pdf");
        payload.put("damage_description", "interior fire damage");

        // Execute transformation chain
        ClaimResult result = transformationService.transform(new ClaimPayload(payload));

        // Expected Result: Initial claim type set to Represented claim
        assertEquals("Represented", result.getClaimType(), "Claim type should be transformed to Represented");

        // Execute routing
        routingService.route(result);
        // Expected Result: communication routing restricted to attorney contact
        assertEquals("attorney", result.getCommunicationRouting(), "Comms routing should be restricted to attorney contact");

        // Execute task generation
        List<TaskRecord> tasks = taskGenerationService.generate(result);
        // Expected Result: tasks created include Attorney Representation Review and Coverage Counsel Review
        assertTrue(tasks.stream().anyMatch(t -> "Attorney Representation Review".equals(t.getTitle())), 
                "Task 'Attorney Representation Review' should be generated");
        assertTrue(tasks.stream().anyMatch(t -> "Coverage Counsel Review".equals(t.getTitle())), 
                "Task 'Coverage Counsel Review' should be generated");

        // Execute diary event creation
        List<DiaryEvent> diaryEvents = diaryEventService.create(result);
        // Expected Result: diary events created for Representation document due
        assertTrue(diaryEvents.stream().anyMatch(d -> "Representation document due".equals(d.getDescription())), 
                "Diary event for representation document due should be created");

        // Execute audit logging
        auditLogService.log(result);
        // Expected Result: audit log records representation flag and workflow routing decision
        assertNotNull(result.getAuditTrail(), "Audit trail should be recorded");
        assertTrue(result.getAuditTrail().containsKey("representation_flag"), 
                "Audit log should record representation flag");
        assertTrue(result.getAuditTrail().containsKey("workflow_routing_decision"), 
                "Audit log should record workflow routing decision");
    }
}
