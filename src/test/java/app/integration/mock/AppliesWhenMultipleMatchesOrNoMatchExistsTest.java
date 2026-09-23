package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingOrchestrationTransformationTest {

    @Mock
    private PolicyClaimsDbClient policyClaimsDbClient;
    @Mock
    private ComplianceAuditService complianceAuditService;
    @Mock
    private DocumentStorageService documentStorageService;

    @InjectMocks
    private ClaimOrchestrationService claimOrchestrationService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = Map.of(
            "claimId", "CLM-12345",
            "policyNumber", "POL-999",
            "incidentDate", "2024-01-15",
            "status", "INITIATED"
        );
    }

    @Test
    void applies_when_multiple_matches_or_no_match_exists() {
        // Scenario: No match exists -> defaults to manual review routing & compliance audit
        when(policyClaimsDbClient.queryPolicy(any())).thenReturn(List.of());
        Map<String, Object> resultNoMatch = claimOrchestrationService.transformAndRoute(claimPayload);
        assertNotNull(resultNoMatch);
        assertEquals("ROUTED_TO_MANUAL_REVIEW", resultNoMatch.get("routingStatus"));
        verify(complianceAuditService).logAuditEvent(eq("CLAIM_INITIATION"), eq("NO_POLICY_MATCH"));
        verify(documentStorageService).storeDocument(eq("DocumentStorage-bucket"), eq("DocumentStorage/CLM-12345.json"), any());

        // Scenario: Multiple matches exist -> defaults to ambiguity resolver routing & structured audit
        when(policyClaimsDbClient.queryPolicy(any())).thenReturn(List.of("POL-999-A", "POL-999-B"));
        Map<String, Object> resultMultiple = claimOrchestrationService.transformAndRoute(claimPayload);
        assertNotNull(resultMultiple);
        assertEquals("ROUTED_TO_AMBIGUITY_RESOLVER", resultMultiple.get("routingStatus"));
        verify(complianceAuditService, times(2)).logAuditEvent(eq("CLAIM_INITIATION"), eq("MULTIPLE_POLICY_MATCHES"));
        verify(documentStorageService, times(2)).storeDocument(eq("DocumentStorage-bucket"), eq("DocumentStorage/CLM-12345.json"), any());
    }
}
