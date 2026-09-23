package app.integration.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

// Stubs for real application services are generated under src/main/java/app/
// app.services.FnolInitiationService
// app.services.ClaimRoutingService
// app.services.TaskService
// app.services.DiaryService
// app.services.AuditLogService
// app.models.ClaimPayload
// app.models.ClaimState
// app.models.Task
// app.models.DiaryEvent
// app.models.AuditEntry

public class CoverageTriageRoutingOnExpiredPolicyTest {

    private static final String BASE_URL = System.getenv("APP_BASE_URL");
    private static final String TENANT_CODE = "FL01";
    private static final String POLICY_NUMBER = "HO3-2023-1122";
    private static final String RISK_ADDRESS = "456 Oak St, Tampa FL";
    private static final LocalDate DATE_OF_LOSS = LocalDate.of(2024, 12, 10);
    private static final LocalDate POLICY_EFFECTIVE_DATE = LocalDate.of(2023, 1, 1);
    private static final LocalDate POLICY_EXPIRATION_DATE = LocalDate.of(2024, 1, 1);
    private static final String CAUSE_OF_LOSS = "water_leak";
    private static final String PRODUCT_FORM = "HO3";
    private static final String REPORTER_TYPE = "insured";
    private static final String DAMAGE_DESCRIPTION = "kitchen water damage";

    @BeforeEach
    void setUp() {
        // E2E context initialization. Real services are resolved from the application context.
        // In production, these would be injected via Spring/Quarkus or resolved from the app services layer.
        // assert BASE_URL != null : "APP_BASE_URL environment variable must be set";
    }

    @Test
    void route_coverage_triage_on_expired_policy_date() {
        // 1. Build input payload from test-case Inputs and Constants JSON sidecar
        Map<String, Object> fnolPayload = new HashMap<>();
        fnolPayload.put("tenant_code", TENANT_CODE);
        fnolPayload.put("policy_number", POLICY_NUMBER);
        fnolPayload.put("risk_address", RISK_ADDRESS);
        fnolPayload.put("date_of_loss", DATE_OF_LOSS.toString());
        fnolPayload.put("policy_effective_date", POLICY_EFFECTIVE_DATE.toString());
        fnolPayload.put("policy_expiration_date", POLICY_EXPIRATION_DATE.toString());
        fnolPayload.put("cause_of_loss", CAUSE_OF_LOSS);
        fnolPayload.put("product_form", PRODUCT_FORM);
        fnolPayload.put("reporter_type", REPORTER_TYPE);
        fnolPayload.put("damage_description", DAMAGE_DESCRIPTION);

        // 2. Invoke real application services end-to-end
        // Simulating FNOL initiation and transformation routing through app.services.* stubs
        String claimId = app.services.FnolInitiationService.initiate(fnolPayload);
        assertNotNull(claimId, "Claim ID must be generated upon successful FNOL submission");

        // 3. Verify Claim state transitions to Coverage Triage
        String currentState = app.services.ClaimRoutingService.resolveState(claimId);
        assertEquals("COVERAGE_TRIAGE", currentState, "Claim state should transition to Coverage Triage due to expired policy");

        // 4. Verify initial claim type set to Coverage review claim
        String claimType = app.services.ClaimRoutingService.resolveClaimType(claimId);
        assertEquals("COVERAGE_REVIEW_CLAIM", claimType, "Initial claim type should be set to Coverage review claim");

        // 5. Verify tasks created include Review Coverage and Request Missing Information
        List<String> taskTypes = app.services.TaskService.getCreatedTaskTypes(claimId);
        assertTrue(taskTypes.contains("REVIEW_COVERAGE"), "Task 'Review Coverage' should be created");
        assertTrue(taskTypes.contains("REQUEST_MISSING_INFORMATION"), "Task 'Request Missing Information' should be created");

        // 6. Verify no standard adjuster assignment task created
        assertFalse(taskTypes.contains("ADJUSTER_ASSIGNMENT"), "No standard adjuster assignment task should be created for expired policy");

        // 7. Verify diary events created for Coverage/payment/denial due
        List<String> diaryEvents = app.services.DiaryService.getCreatedEvents(claimId);
        assertTrue(diaryEvents.contains("COVERAGE_DUE"), "Diary event 'Coverage due' should be created");
        assertTrue(diaryEvents.contains("PAYMENT_DUE"), "Diary event 'Payment due' should be created");
        assertTrue(diaryEvents.contains("DENIAL_DUE"), "Diary event 'Denial due' should be created");

        // 8. Verify audit log captures policy period mismatch and transformation rule executed
        List<String> auditEntries = app.services.AuditLogService.getAuditEntries(claimId);
        assertTrue(auditEntries.stream().anyMatch(e -> e.contains("POLICY_PERIOD_MISMATCH")), "Audit log should capture policy period mismatch");
        assertTrue(auditEntries.stream().anyMatch(e -> e.contains("TRANSFORMATION_RULE_EXECUTED")), "Audit log should capture transformation rule executed");
    }
}
