package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Ho3BroadCoverageSubjectToExclusionsDp3DwellingTest {

    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private RulesEngineService rulesEngineService;
    @Mock
    private ClaimDataStandardizationService claimStandardizationService;

    private static final String TEST_ID = "claim-id-ho3-dp3-001";
    private Map<String, Object> inputPayload;

    @BeforeEach
    void setUp() {
        // Simulates claim_data_standardization_transformation_valida entity
        inputPayload = Map.of(
            "id", TEST_ID,
            "payload", Map.of(
                "coverageType", "HO3",
                "dwellingForm", "DP3",
                "exclusions", List.of("earthquake", "flood"),
                "endorsements", List.of("water_backup", "equipment_breakdown"),
                "standardizationStatus", "PENDING"
            )
        );
    }

    @Test
    void ho3_broad_coverage_subject_to_exclusions_dp3_dwelling_focused_with_specific_endorsements() {
        // Arrange: Mock external I/O contracts (S3 & DynamoDB) to satisfy NFR: security & availability
        when(documentStoreService.read(anyString(), anyString())).thenReturn(inputPayload);
        when(policyValidationService.query(anyString(), anyString()))
            .thenReturn(Map.of("policyType", "HO3", "dwellingCoverage", "DP3", "isValid", true));
        when(rulesEngineService.evaluate(anyString()))
            .thenReturn(Map.of("decision", "APPROVED", "reasonCode", "HO3_BROAD_EXCLUSIONS_DP3_ENDORSEMENTS"));

        // Act: Execute standardization decision logic
        Map<String, Object> result = claimStandardizationService.validateAndDecide(TEST_ID, inputPayload);

        // Assert: Verify decision outcome matches HO3/DP3 scenario
        assertNotNull(result, "Decision payload must not be null");
        assertEquals("APPROVED", result.get("decision"), "HO3 with DP3 dwelling should be approved under standard rules");
        assertTrue(result.containsKey("payload"), "Result must contain standardized payload");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> standardizedPayload = (Map<String, Object>) result.get("payload");
        assertEquals("HO3", standardizedPayload.get("coverageType"));
        assertEquals("DP3", standardizedPayload.get("dwellingForm"));
        assertEquals(List.of("earthquake", "flood"), standardizedPayload.get("exclusions"));
        assertEquals(List.of("water_backup", "equipment_breakdown"), standardizedPayload.get("endorsements"));

        // Verify external I/O interactions (thread-safe mock verification)
        verify(documentStoreService, times(1)).read(eq("DocumentStoreService-bucket"), eq("DocumentStoreService/" + TEST_ID + ".json"));
        verify(policyValidationService, times(1)).query(eq("PolicyValidationService_table"), eq("pk"));
        verify(rulesEngineService, times(1)).evaluate(eq("RulesEngineService_table"));

        // NFR: Input validation & structured logging simulation
        assertDoesNotThrow(() -> claimStandardizationService.validateAndDecide(TEST_ID, inputPayload),
            "Input validation should pass for well-formed HO3/DP3 payload");
    }
}
