package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RiskAddressNormalizedTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationService claimDataStandardizationService;

    @BeforeEach
    void setUp() {
        // Mocks initialized via MockitoExtension; ensures thread-safe isolation per test
    }

    @Test
    void risk_address_normalized() {
        // Arrange: Prepare test payload with raw risk address data
        String entityId = "claim-std-001";
        Map<String, Object> rawPayload = Map.of(
            "riskAddress", "123 main st.",
            "state", "ny",
            "zip", "10001"
        );

        // Mock external I/O contracts (S3 & DynamoDB) to avoid live infra calls
        when(documentStoreService.read(anyString(), anyString())).thenReturn(rawPayload);
        when(policyValidationService.getItem(anyString(), anyString())).thenReturn(Map.of("policyStatus", "ACTIVE"));
        when(rulesEngineService.getItem(anyString(), anyString())).thenReturn(Map.of("rulesVersion", "v2.1"));

        // Act: Execute validation and decision logic
        Map<String, Object> standardizedPayload = claimDataStandardizationService.processValidationDecision(entityId, rawPayload);

        // Assert: Verify normalization and decision output
        assertNotNull(standardizedPayload, "Standardized payload must not be null");
        assertEquals("123 MAIN ST.", standardizedPayload.get("riskAddress"), "Risk address should be normalized to uppercase");
        assertEquals("NY", standardizedPayload.get("state"), "State abbreviation should be standardized");
        assertEquals("10001", standardizedPayload.get("zip"), "ZIP code should remain valid after normalization");
        assertTrue(standardizedPayload.containsKey("validationStatus"), "Decision status must be present per validation contract");
        assertEquals("VALID", standardizedPayload.get("validationStatus"), "Decision should be VALID for correctly normalized address");

        // Verify infra I/O contracts were invoked with correct logical names and patterns
        verify(documentStoreService).read("DocumentStoreService-bucket", String.format("DocumentStoreService/%s.json", entityId));
        verify(policyValidationService).getItem("PolicyValidationService_table", entityId);
        verify(rulesEngineService).getItem("RulesEngineService_table", entityId);
    }
}
