package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:decision:validation.
 * Verifies HW2 product form validation logic for non-wind causes.
 */
@ExtendWith(MockitoExtension.class)
class Hw2ProductFormValidationTest {

    @Mock
    private PolicyRetrievalService policyRetrievalService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private ClaimPersistenceService claimPersistenceService;

    @Mock
    private TaskCreationService taskCreationService;

    @InjectMocks
    private ClaimDataStandardizationDecisionValidator validator;

    @Test
    void validate_hw2_non_wind_cause_triggers_coverage_review() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        String productId = "HW2";
        String causeOfLoss = "fire";
        String policyStatus = "active";
        LocalDate lossDate = LocalDate.of(2024, 6, 15);

        PolicyInfo mockPolicy = new PolicyInfo(productId, policyStatus, LocalDate.now().minusYears(1));
        when(policyRetrievalService.getPolicyByClaimId(eq(claimId))).thenReturn(mockPolicy);

        Map<String, Object> inputPayload = Map.of(
            "cause_of_loss", causeOfLoss,
            "loss_date", lossDate.toString(),
            "product_form", productId
        );
        when(documentStoreService.getDocumentPayload(eq(claimId))).thenReturn(inputPayload);

        // Act
        StandardizationResult result = validator.standardizeAndValidate(claimId);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals("Coverage review claim", result.initialClaimType(), 
            "Initial claim type should be Coverage review claim for non-wind cause on HW2");

        Map<String, Object> standardPayload = result.payload();
        assertTrue((Boolean) standardPayload.get("cause_mismatch_flag"), 
            "Cause mismatch flag should be true");

        // Verify task created for coverage specialist
        verify(taskCreationService, times(1))
            .createTask(eq("coverage specialist"), any(TaskPayload.class));

        // Verify no adjuster assignment task generated
        verify(taskCreationService, never())
            .createTask(eq("adjuster"), any(TaskPayload.class));

        // Verify claim data persisted with standardized payload
        verify(claimPersistenceService, times(1))
            .saveClaimData(argThat(entity -> 
                entity.id().equals(claimId) && 
                (Boolean) entity.payload().get("cause_mismatch_flag")
            ));
    }

    // Minimal DTOs for test compilation context
    record PolicyInfo(String productId, String status, LocalDate effectiveDate) {}
    record TaskPayload(String assignee, String taskType) {}
    record StandardizationResult(String initialClaimType, Map<String, Object> payload) {}
    record ClaimDataEntity(String id, Map<String, Object> payload) {}
}
