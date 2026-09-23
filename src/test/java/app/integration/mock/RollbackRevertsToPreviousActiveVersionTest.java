package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationValidationDecisionService service;

    @Test
    void rollback_reverts_to_previous_active_version() {
        // Given
        String claimId = "claim-123";
        String currentVersionId = "v2";
        String previousActiveVersionId = "v1";
        Map<String, Object> previousPayload = Map.of("version", previousActiveVersionId, "status", "active");

        when(documentStoreService.readObject(anyString(), anyString())).thenReturn(Optional.of(previousPayload));
        when(policyValidationService.validate(anyString())).thenReturn(true);
        when(rulesEngineService.execute(anyString(), any())).thenReturn(true);

        // When
        Map<String, Object> result = service.rollbackClaimData(claimId, currentVersionId);

        // Then
        assertNotNull(result, "Rollback should return a payload");
        assertEquals(previousActiveVersionId, result.get("version"), "Should revert to previous active version");
        verify(documentStoreService).readObject(eq("DocumentStoreService-bucket"), anyString());
        verify(policyValidationService).validate(eq("PolicyValidationService_table"));
        verify(rulesEngineService).execute(eq("RulesEngineService_table"), any());
    }
}
