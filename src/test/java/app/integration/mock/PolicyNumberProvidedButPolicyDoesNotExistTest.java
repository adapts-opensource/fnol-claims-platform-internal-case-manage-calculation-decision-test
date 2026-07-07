package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Claim Data Standardization: decision: transformation.
 * Verifies handling of policy number provided but policy does not exist in PAS.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private PasPolicyLookupService pasPolicyLookupService;

    private ClaimDataStandardizationService claimDataStandardizationService;

    @BeforeEach
    void setUp() {
        claimDataStandardizationService = new ClaimDataStandardizationService(pasPolicyLookupService);
    }

    @Test
    void policy_number_provided_but_policy_does_not_exist_in_pas() {
        // Arrange
        String policyNumber = "POL-98765";
        String claimId = "CLM-12345";
        Map<String, Object> inputPayload = Map.of("policyNumber", policyNumber);

        when(pasPolicyLookupService.findByPolicyNumber(policyNumber)).thenReturn(Optional.empty());

        // Act
        Map<String, Object> result = claimDataStandardizationService.transform(claimId, inputPayload);

        // Assert
        assertNotNull(result, "Transformed result should not be null");
        assertEquals(claimId, result.get("id"), "Claim ID should match input");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");
        assertNotNull(payload, "Payload map should not be null");
        
        assertEquals(false, payload.get("policyExists"), "Policy existence flag should be false");
        assertEquals("POLICY_NOT_FOUND", payload.get("policyStatus"), "Policy status should indicate not found");
        assertEquals(policyNumber, payload.get("policyNumber"), "Original policy number should be preserved");
        
        // Verify external I/O contracts are mocked and not called live
        verify(pasPolicyLookupService).findByPolicyNumber(policyNumber);
        verifyNoMoreInteractions(pasPolicyLookupService);
    }
}
