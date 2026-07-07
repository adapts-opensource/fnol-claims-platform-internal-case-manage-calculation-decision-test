package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidApiIntakeTransformationTest {

    @Mock
    private PolicyValidationClient policyValidationClient;
    @Mock
    private ClaimModelTransformer claimModelTransformer;
    @Mock
    private TaskOrchestrator taskOrchestrator;
    @Mock
    private AuditLogWriter auditLogWriter;

    @InjectMocks
    private InsuredEngagementTransformationService insuredEngagementService;

    private Map<String, Object> validApiIntakePayload;

    @BeforeEach
    void setUp() {
        validApiIntakePayload = new HashMap<>();
        validApiIntakePayload.put("channel", "API");
        validApiIntakePayload.put("policy_number", "POL-12345");
        validApiIntakePayload.put("insured_name", "John Doe");
        validApiIntakePayload.put("loss_date", "2024-05-15");
        validApiIntakePayload.put("cause_of_loss", "Wind");
        validApiIntakePayload.put("product_form", "HO3");
    }

    @Test
    void transform_valid_api_intake_to_claim_model() {
        // Arrange: Mock external I/O clients (Policy DB, Task Queue, Audit Log)
        Map<String, Object> policyRecord = Map.of("policyNumber", "POL-12345", "status", "ACTIVE", "productForm", "HO3");
        when(policyValidationClient.fetchPolicy(anyString())).thenReturn(Optional.of(policyRecord));
        when(policyValidationClient.validatePolicyMatch(anyMap(), anyMap())).thenReturn(true);

        Map<String, Object> transformedClaimModel = Map.of("id", "CLM-98765", "status", "Submitted", "policyMatch", true);
        when(claimModelTransformer.transform(anyMap())).thenReturn(transformedClaimModel);

        doNothing().when(taskOrchestrator).createIntakeReviewTask(anyString(), anyString());
        doNothing().when(auditLogWriter).persistAuditEntry(anyString(), anyMap());

        // Act: Execute transformation and validation flow
        Map<String, Object> result = insuredEngagementService.processIntake(validApiIntakePayload);

        // Assert: Verify expected outcomes
        assertNotNull(result, "Claim model should be created");
        assertEquals("Submitted", result.get("status"), "Claim status should be Submitted");
        assertTrue((Boolean) result.get("policyMatch"), "Policy match should be found");
        
        verify(taskOrchestrator).createIntakeReviewTask("POL-12345", "John Doe", "Intake Review");
        verify(auditLogWriter).persistAuditEntry("API", validApiIntakePayload);
    }
}
