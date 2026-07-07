package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeIdentifyExistingClaimsThatAreLikelyDuplicates {

    @Mock
    private ClaimTransformationService claimTransformationService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private Map<String, Object> currentFnolPayload;
    private List<Map<String, Object>> existingClaims;

    @BeforeEach
    void setUp() {
        // Simulate claim_data_standardization_calculation_transform payload
        currentFnolPayload = Map.of(
            "id", "fnol-123",
            "policyNumber", "POL-98765",
            "dateOfLoss", "2023-10-25",
            "claimantName", "John Doe",
            "vehicleLicensePlate", "ABC-1234"
        );

        existingClaims = List.of(
            Map.of("id", "claim-101", "policyNumber", "POL-98765", "vehicleLicensePlate", "ABC-1234", "status", "OPEN"),
            Map.of("id", "claim-102", "policyNumber", "POL-11111", "vehicleLicensePlate", "XYZ-9999", "status", "CLOSED")
        );

        // Mock infrastructure contracts (DynamoDB & S3 abstractions)
        lenient().when(rulesEngineDecisionService.fetchRulesByPolicy("POL-98765")).thenReturn(Map.of("dedupThreshold", 0.85));
        lenient().when(workflowTaskRouter.getRoutingConfig()).thenReturn(Map.of("dedupEnabled", true));
        lenient().when(auditDiaryStore.writeAuditEntry(anyString(), anyString())).thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/fnol-123.json");
    }

    @Test
    void purpose_identify_existing_claims_that_are_likely_duplicates_of_the_current_fnol() {
        // Arrange: Mock transformation service behavior
        when(claimTransformationService.identifyLikelyDuplicates(currentFnolPayload, existingClaims))
            .thenReturn(List.of("claim-101"));

        // Act: Execute deduplication logic
        List<String> duplicateClaimIds = claimTransformationService.identifyLikelyDuplicates(currentFnolPayload, existingClaims);

        // Assert: Verify results meet feature requirements
        assertNotNull(duplicateClaimIds, "Duplicate identification should return a non-null list");
        assertEquals(1, duplicateClaimIds.size(), "Should identify exactly one likely duplicate");
        assertTrue(duplicateClaimIds.contains("claim-101"), "Should contain the matching claim ID");
        assertFalse(duplicateClaimIds.contains("claim-102"), "Should not contain the non-matching claim ID");

        // Verify service interactions and NFR compliance (logging/audit)
        verify(claimTransformationService, times(1)).identifyLikelyDuplicates(currentFnolPayload, existingClaims);
        verify(rulesEngineDecisionService, times(1)).fetchRulesByPolicy("POL-98765");
        verify(workflowTaskRouter, times(1)).getRoutingConfig();
        verify(auditDiaryStore, times(1)).writeAuditEntry(eq("fnol-123"), anyString());
    }
}
