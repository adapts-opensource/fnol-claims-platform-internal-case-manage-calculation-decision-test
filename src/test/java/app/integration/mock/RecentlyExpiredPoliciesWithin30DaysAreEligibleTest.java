package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingOrchestrationTransformationTest {

    @Mock
    private PolicyClaimsDB policyClaimsDB;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private DocumentStorage documentStorage;

    private ClaimTransformationOrchestrator orchestrator;

    @Captor
    private ArgumentCaptor<Map<String, Object>> itemPayloadCaptor;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimTransformationOrchestrator(policyClaimsDB, complianceAuditService, documentStorage);
    }

    @Test
    void recentlyExpiredPoliciesWithin30DaysAreEligible() {
        // Arrange
        String policyId = "POL-98765";
        LocalDate expirationDate = LocalDate.now().minusDays(15);
        Map<String, Object> originalPolicy = new HashMap<>();
        originalPolicy.put("pk", "POLICY#" + policyId);
        originalPolicy.put("status", "EXPIRED");
        originalPolicy.put("expirationDate", expirationDate.toString());
        originalPolicy.put("eligibleForClaim", false);

        when(policyClaimsDB.getItem(anyString())).thenReturn(originalPolicy);

        // Act
        String transformationStatus = orchestrator.transform(policyId);

        // Assert
        assertEquals("TRANSFORMED", transformationStatus);
        
        verify(policyClaimsDB).updateItem(anyString(), itemPayloadCaptor.capture());
        Map<String, Object> updatedItem = itemPayloadCaptor.getValue();
        assertTrue(Boolean.parseBoolean(String.valueOf(updatedItem.get("eligibleForClaim"))),
            "Policies expired within 30 days must be marked eligible for claim initiation");

        verify(complianceAuditService).writeRecord(anyString(), anyString());
        verify(documentStorage).putObject(anyString(), anyString());
    }
}
