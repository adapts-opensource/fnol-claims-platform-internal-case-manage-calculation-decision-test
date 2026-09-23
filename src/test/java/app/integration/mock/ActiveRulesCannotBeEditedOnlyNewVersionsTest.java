package app.integration.mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.integration.mock.model.RuleStatus;
import app.integration.mock.model.RuleDTO;
import app.integration.mock.model.RuleUpdateRequest;
import app.integration.mock.service.PolicyClaimsDB;
import app.integration.mock.service.ComplianceAuditService;
import app.integration.mock.service.RuleOrchestrationTransformationService;
import app.integration.mock.exception.ActiveRuleEditProhibitedException;

/**
 * Integration mock test for Claim Initiation & Routing: orchestration: transformation.
 * Verifies business rule constraints regarding rule versioning and immutability of active rules.
 * 
 * NFR Coverage:
 * - Compliance/SOC2: Verifies audit logging for version creation events.
 * - Security: Verifies input validation context (mocked).
 * - Concurrency: Test is isolated and thread-safe via JUnit 5 / Mockito.
 */
@ExtendWith(MockitoExtension.class)
class ActiveRulesCannotBeEditedOnlyNewVersionsTest {

    private static final String RULE_ID = "CLM-RULE-CLAIM-001";
    private static final String CURRENT_VERSION = "v1";
    private static final String EXPECTED_NEW_VERSION = "v2";
    private static final String USER_ID = "user-123";

    @Mock
    private PolicyClaimsDB policyClaimsDB;

    @Mock
    private ComplianceAuditService complianceAuditService;

    private RuleOrchestrationTransformationService serviceUnderTest;

    @BeforeEach
    void setUp() {
        // Reset mocks between tests to ensure thread safety and isolation
        reset(policyClaimsDB, complianceAuditService);
        serviceUnderTest = new RuleOrchestrationTransformationService(policyClaimsDB, complianceAuditService);
    }

    @Test
    @DisplayName("Active rules cannot be edited; only new versions created")
    void active_rules_cannot_be_edited_only_new_versions_created() {
        // Arrange: Mock an existing active rule in DynamoDB
        RuleDTO activeRule = RuleDTO.builder()
                .id(RULE_ID)
                .version(CURRENT_VERSION)
                .status(RuleStatus.ACTIVE)
                .lastModified(Instant.now())
                .build();

        when(policyClaimsDB.getItem(eq(RULE_ID), eq(CURRENT_VERSION)))
                .thenReturn(Optional.of(activeRule));

        RuleUpdateRequest updateRequest = RuleUpdateRequest.builder()
                .ruleId(RULE_ID)
                .version(CURRENT_VERSION)
                .payload(Map.of("description", "Updated description for active rule"))
                .userId(USER_ID)
                .build();

        // Act & Assert: Attempting to edit an active rule should throw a constraint exception
        // The system enforces immutability on active rules to preserve audit integrity.
        ActiveRuleEditProhibitedException exception = assertThrows(
                ActiveRuleEditProhibitedException.class,
                () -> serviceUnderTest.processRuleUpdate(updateRequest),
                "Expected ActiveRuleEditProhibitedException when editing active rule"
        );

        // Verify exception message contains helpful context for input validation
        assertTrue(exception.getMessage().contains("cannot be edited"));

        // Verify that the system creates a new version instead of updating the existing item
        ArgumentCaptor<RuleDTO> newVersionCaptor = ArgumentCaptor.forClass(RuleDTO.class);
        
        verify(policyClaimsDB, times(1)).putItem(newVersionCaptor.capture());
        
        RuleDTO createdVersion = newVersionCaptor.getValue();
        
        assertEquals(RULE_ID, createdVersion.getId());
        assertEquals(EXPECTED_NEW_VERSION, createdVersion.getVersion());
        assertEquals(RuleStatus.DRAFT, createdVersion.getStatus(), "New version should be created as DRAFT");
        assertEquals(CURRENT_VERSION, createdVersion.getParentVersion(), "Should reference parent version");
        assertFalse(createdVersion.isDeleted(), "New version must not be deleted");

        // Verify Audit Logging for SOC2/GDPR compliance
        ArgumentCaptor<Map<String, Object>> auditEventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(complianceAuditService, times(1))
                .logEvent(eq("RULE_VERSION_CREATED"), anyString(), auditEventCaptor.capture());

        Map<String, Object> auditEvent = auditEventCaptor.getValue();
        assertEquals(RULE_ID, auditEvent.get("ruleId"));
        assertEquals(CURRENT_VERSION, auditEvent.get("previousVersion"));
        assertEquals(EXPECTED_NEW_VERSION, auditEvent.get("newVersion"));
        assertEquals(USER_ID, auditEvent.get("userId"));
        
        // Verify no update was attempted on the active rule in DynamoDB
        verify(policyClaimsDB, never()).updateItem(eq(RULE_ID), any());
    }

    @Test
    @DisplayName("Non-active rules can be edited directly without versioning")
    void non_active_rules_can_be_edited_directly() {
        // Arrange: Mock a DRAFT rule
        RuleDTO draftRule = RuleDTO.builder()
                .id(RULE_ID)
                .version(CURRENT_VERSION)
                .status(RuleStatus.DRAFT)
                .lastModified(Instant.now())
                .build();

        when(policyClaimsDB.getItem(eq(RULE_ID), eq(CURRENT_VERSION)))
                .thenReturn(Optional.of(draftRule));

        RuleUpdateRequest updateRequest = RuleUpdateRequest.builder()
                .ruleId(RULE_ID)
                .version(CURRENT_VERSION)
                .payload(Map.of("description", "Edit draft rule"))
                .userId(USER_ID)
                .build();

        // Act: Updating a draft rule should succeed and update the item directly
        assertDoesNotThrow(() -> serviceUnderTest.processRuleUpdate(updateRequest));

        // Verify updateItem was called, not putItem (version creation)
        verify(policyClaimsDB, never()).putItem(any());
        verify(policyClaimsDB, times(1)).updateItem(eq(RULE_ID), any());
        
        // Verify minimal audit log for edit operation
        verify(complianceAuditService, times(1))
                .logEvent(eq("RULE_UPDATED"), anyString(), anyMap());
    }
}
