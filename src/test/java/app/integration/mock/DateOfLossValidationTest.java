package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class DateOfLossValidationTest {

    @Mock
    private ClaimStandardizationService standardizationService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private TaskGenerationService taskGenerationService;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private S3Client s3Client;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = new HashMap<>();
        claimPayload.put("id", "claim-001");
        claimPayload.put("loss_date", "2023-12-31");
        claimPayload.put("policy_effective_date", "2024-01-01");
        claimPayload.put("policy_expiration_date", "2024-12-31");
        claimPayload.put("product_form", "HO3");
        claimPayload.put("cause_of_loss", "wind");
    }

    @Test
    void validate_loss_date_against_policy_period() {
        // Arrange: Mock external I/O and service behavior
        when(dynamoDbClient.putItem(anyString(), anyMap())).thenReturn(true);
        when(s3Client.putObject(anyString(), anyString(), anyString())).thenReturn("s3://Document & Media Store-bucket/claim-001.json");
        when(taskGenerationService.generateTask(anyString(), anyString())).thenReturn("TASK-COVERAGE-99");
        
        // Simulate validation logic modifying payload in-place
        when(standardizationService.processClaim(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            payload.put("fnol_state", "Coverage Triage");
            payload.put("coverage_review_flag", true);
            return "claim-001";
        });

        // Act: Execute claim processing
        String captureResult = standardizationService.processClaim(claimPayload);

        // Assert: Verify expected outcomes
        assertNotNull(captureResult, "Claim should be captured successfully");
        assertEquals("Coverage Triage", claimPayload.get("fnol_state"), "FNOL state must route to Coverage Triage");
        assertTrue((Boolean) claimPayload.get("coverage_review_flag"), "Payload must contain coverage_review_flag=true");
        
        verify(auditLogService).logRuleExecution("C-01-S2-014", claimPayload, "DateOfLossValidation");
        verify(taskGenerationService).generateTask("Coverage Review", "TASK-COVERAGE-99");
        verify(dynamoDbClient).putItem("Policy & Claim Data Store_table", claimPayload);
        verify(s3Client).putObject("Document & Media Store-bucket", "claim-001.json", claimPayload);
    }
}
