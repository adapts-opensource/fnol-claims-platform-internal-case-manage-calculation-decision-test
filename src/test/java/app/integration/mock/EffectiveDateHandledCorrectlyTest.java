package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Data Standardization: validation: decision.
 * Verifies that the effective date in the claim payload is correctly processed
 * and results in the expected validation decision.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionValidationTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationDecisionService claimDataStandardizationDecisionService;

    @BeforeEach
    void setUp() {
        // Mock S3 write contract
        when(documentStoreService.writeObject(anyString(), anyString(), any(Map.class)))
                .thenReturn("s3://DocumentStoreService-bucket/claim-" + UUID.randomUUID() + ".json");
        
        // Mock DynamoDB rules contract
        when(rulesEngineService.getItemPayload(anyString(), anyString()))
                .thenReturn(Map.of("rule_id", "STD_DATE_01", "severity", "INFO"));
        
        // Mock DynamoDB policy contract
        when(policyValidationService.getItemPayload(anyString(), anyString()))
                .thenReturn(Map.of("policy_type", "AUTO", "status", "ACTIVE"));
    }

    @Test
    void effective_date_handled_correctly() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "effective_date", "2024-06-15",
                "claim_type", "AUTO_COLLISION",
                "policy_number", "POL-112233"
        );

        // Act
        String decision = claimDataStandardizationDecisionService.validateAndDecide(payload);

        // Assert
        assertEquals("ACCEPTED", decision, "Payload with valid effective_date should yield ACCEPTED decision");
        assertEquals(claimId, payload.get("id"));
        assertNotNull(payload.get("effective_date"), "Effective date must be preserved in standardized payload");

        // Verify infrastructure I/O contracts are invoked exactly once
        verify(documentStoreService, times(1)).writeObject(eq("DocumentStoreService-bucket"), eq("claims/" + claimId + ".json"), any(Map.class));
        verify(rulesEngineService, times(1)).getItemPayload(eq("RulesEngineService_table"), eq("pk:" + claimId));
        verify(policyValidationService, times(1)).getItemPayload(eq("PolicyValidationService_table"), eq("pk:" + claimId));
    }
}
