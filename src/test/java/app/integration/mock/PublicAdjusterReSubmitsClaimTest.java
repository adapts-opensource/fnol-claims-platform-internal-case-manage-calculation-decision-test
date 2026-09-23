package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PublicAdjusterReSubmitsClaimTest {

    @Mock
    private CacheReferenceDataClient cacheClient;

    @Mock
    private ClaimsPolicyDataStoreClient claimsDataClient;

    @Mock
    private CommunicationAcknowledgmentClient communicationClient;

    @InjectMocks
    private ClaimInitiationRoutingDecisionService decisionService;

    private String claimId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        claimId = "claim-init-routing-001";
        payload = Map.of(
                "submitterRole", "PUBLIC_ADJUSTER",
                "actionType", "RESUBMIT",
                "policyId", "POL-2023-8842",
                "incidentTimestamp", "2023-11-15T10:30:00Z",
                "validationStatus", "PENDING_REVIEW"
        );
    }

    @Test
    void public_adjuster_re_submits_claim() {
        // Arrange
        String cacheKey = "Cache & Reference Data:cache:" + claimId;
        when(cacheClient.getValue(eq(cacheKey))).thenReturn("ROUTING_RULE_V2");
        when(claimsDataClient.getItem(anyString(), anyString())).thenReturn(Map.of("status", "DRAFT", "version", "1.0"));
        doNothing().when(communicationClient).sendAcknowledgment(anyString(), anyList(), anyString());

        // Act
        Map<String, Object> result = decisionService.evaluateClaimInitiation(payload, claimId);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertEquals("ROUTED_TO_SPECIALIZED_HANDLER", result.get("routingDecision"));
        assertTrue((boolean) result.get("validationPassed"), "Payload validation should pass");
        assertEquals(claimId, result.get("claimId"));

        // Verify infrastructure contracts were invoked securely and idempotently
        verify(cacheClient, times(1)).getValue(eq(cacheKey));
        verify(claimsDataClient, times(1)).getItem(anyString(), anyString());
        verify(communicationClient, times(1)).sendAcknowledgment(eq(claimId), anyList(), anyString());
    }
}
