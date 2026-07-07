package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTest {

    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimDataStandardizationDecisionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClaimDataStandardizationDecisionValidator(
            documentStoreService,
            policyValidationService,
            rulesEngineService
        );
    }

    @Test
    void decision_dol_validity_rule_within_period_proceed_outside_coverage_review_expected_outcome_status_update_reviewer_assignment() {
        // Given: Claim payload with DOL within valid period
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> inputPayload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "dateOfLoss", "2023-11-20",
                "policyId", "POL-12345",
                "claimType", "AUTO"
            )
        );

        // Mock Rules Engine: DOL within period -> proceed
        when(rulesEngineService.evaluate(anyString(), anyString()))
            .thenReturn(Map.of("decision", "within_period", "action", "proceed"));

        // Mock Policy Validation: Coverage review required
        when(policyValidationService.validate(eq(claimId), anyString()))
            .thenReturn(Map.of("reviewRequired", true, "assignedReviewer", "reviewer-001"));

        // Mock S3 Document Store: Successful write
        when(documentStoreService.store(anyString(), anyString(), any(Map.class)))
            .thenReturn("s3://DocumentStoreService-bucket/DocumentStoreService/" + claimId + ".json");

        // When: Process claim data through standardization/decision pipeline
        Map<String, Object> result = validator.processClaimData(inputPayload);

        // Then: Verify expected outcome - status update & reviewer assignment
        assertNotNull(result, "Decision result must not be null");
        assertEquals("STATUS_UPDATED", result.get("claimStatus"), "Claim status should be updated");
        assertEquals("reviewer-001", result.get("assignedReviewer"), "Reviewer should be assigned for coverage review");

        // Verify external I/O interactions (mocked, no live calls)
        verify(rulesEngineService).evaluate(anyString(), anyString());
        verify(policyValidationService).validate(eq(claimId), anyString());
        verify(documentStoreService).store(anyString(), anyString(), any(Map.class));
    }
}
