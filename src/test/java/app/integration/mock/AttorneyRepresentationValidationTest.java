package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Insured Engagement & Tracking:transformation:validation
 * Specifically verifying attorney representation logic.
 */
@ExtendWith(MockitoExtension.class)
public class AttorneyRepresentationValidationTest {

    @Mock
    private CommunicationRestrictionService communicationService;

    @Mock
    private TaskCreationService taskService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private DataStoreService dataStoreService;

    @InjectMocks
    private InsuredEngagementValidator insuredEngagementValidator;

    private Map<String, Object> testPayload;
    private String testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID().toString();
        testPayload = new HashMap<>();
        testPayload.put("id", testId);
        testPayload.put("reporter_type", "Attorney");
        testPayload.put("attorney_name", "Jane Smith");
        testPayload.put("letter_of_representation", "uploaded");
        testPayload.put("direct_contact", false);
    }

    @Test
    void validate_attorney_representation_and_restrict_communications() {
        // Given: Inputs for attorney representation scenario
        String claimId = "CLM-1001";
        String attorneyName = "Jane Smith";
        String letterStatus = "uploaded";

        // Mock external behaviors
        when(communicationService.restrictDirectContact(claimId, attorneyName)).thenReturn(true);
        when(taskService.createReviewTask(eq("Attorney Representation Review"), anyString(), anyString())).thenReturn("TASK-999");
        when(auditLogService.recordEvent(anyString(), anyString(), anyString())).thenReturn("LOG-888");
        when(dataStoreService.saveEntity(anyString(), anyMap())).thenReturn("STORED-ITEM");

        // When: Execute validation and transformation
        InsuredEngagementResult result = insuredEngagementValidator.validateAndTransform(testPayload);

        // Then: Verify expected results
        // 1. Claim flagged as attorney represented
        assertNotNull(result);
        assertTrue(result.isAttorneyRepresented(), "Claim should be flagged as attorney represented");
        assertEquals(attorneyName, result.getAttorneyName(), "Attorney name should be captured");

        // 2. Direct communications restricted
        verify(communicationService, times(1)).restrictDirectContact(claimId, attorneyName);

        // 3. Attorney Representation Review task created
        verify(taskService, times(1)).createReviewTask(
                eq("Attorney Representation Review"),
                eq(claimId),
                eq(attorneyName)
        );

        // 4. Communication log records representation change
        verify(auditLogService, times(1)).recordEvent(
                eq(testId),
                eq("REPRESENTATION_CHANGE"),
                eq("Attorney representation updated: " + attorneyName)
        );

        // 5. Data store updated with validation result
        verify(dataStoreService, times(1)).saveEntity(eq(testId), anyMap());
    }

    /**
     * Mock interfaces representing external I/O contracts
     */
    interface CommunicationRestrictionService {
        boolean restrictDirectContact(String claimId, String attorneyName);
    }

    interface TaskCreationService {
        String createReviewTask(String taskType, String claimId, String context);
    }

    interface AuditLogService {
        String recordEvent(String entityId, String eventType, String description);
    }

    interface DataStoreService {
        String saveEntity(String id, Map<String, Object> payload);
    }

    /**
     * Result model for the validation outcome
     */
    record InsuredEngagementResult(
            boolean attorneyRepresented,
            String attorneyName,
            String taskId,
            String logId
    ) {
        public InsuredEngagementResult {
            this.attorneyRepresented = attorneyRepresented;
            this.attorneyName = attorneyName;
        }
    }
}
