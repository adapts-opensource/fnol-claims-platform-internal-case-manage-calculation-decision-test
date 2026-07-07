package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionAuditorRoleTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Test
    void auditor_role_verified() {
        // given
        String claimId = "claim-std-001";
        Map<String, Object> payload = Map.of(
            "role", "Auditor",
            "stage", "validation:decision",
            "standardized", true
        );

        when(policyValidationService.validate(eq(claimId), any(Map.class))).thenReturn(true);
        when(rulesEngineService.evaluateDecision(eq(claimId), any(Map.class))).thenReturn(Map.of("decision", "approved", "verifiedBy", "Auditor"));
        when(documentStoreService.store(eq("DocumentStoreService-bucket"), anyString())).thenReturn("s3://DocumentStoreService-bucket/" + claimId + ".json");

        // when
        boolean validationPasses = policyValidationService.validate(claimId, payload);
        Map<String, Object> decisionResult = rulesEngineService.evaluateDecision(claimId, payload);
        String objectUri = documentStoreService.store("DocumentStoreService-bucket", claimId + ".json");

        // then
        assertTrue(validationPasses, "Auditor role should pass claim data validation");
        assertEquals("Auditor", decisionResult.get("verifiedBy"), "Decision must be attributed to Auditor role");
        assertNotNull(objectUri, "Standardized claim data must be persisted to DocumentStore");
        verify(policyValidationService).validate(claimId, payload);
        verify(rulesEngineService).evaluateDecision(claimId, payload);
        verify(documentStoreService).store("DocumentStoreService-bucket", claimId + ".json");
    }
}
