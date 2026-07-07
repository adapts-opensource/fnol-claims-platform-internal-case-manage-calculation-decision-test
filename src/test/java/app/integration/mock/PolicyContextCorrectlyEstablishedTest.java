package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization: validation: decision.
 * Verifies that policy context is correctly established using mocked infrastructure.
 * Ensures no live calls to DynamoDB or external services during validation.
 */
@ExtendWith(MockitoExtension.class)
public class PolicyContextEstablishmentMockTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @InjectMocks
    private ClaimDataStandardizationService claimDataStandardizationService;

    @Test
    void policy_context_correctly_established() {
        // Arrange: Define claim data with policy identifier
        String claimId = "claim-test-001";
        Map<String, Object> payload = Map.of("policyNumber", "POL-STD-999");

        // Mock policy context response from DynamoDB-backed service
        Map<String, Object> expectedPolicyContext = Map.of(
            "policyId", "POL-STD-999",
            "status", "ACTIVE",
            "coverageLimit", 100000.0
        );

        when(policyValidationService.retrieveContext("POL-STD-999"))
            .thenReturn(expectedPolicyContext);

        // Act: Execute validation/decision logic
        claimDataStandardizationService.processValidation(claimId, payload);

        // Assert: Verify policy context service was called correctly
        verify(policyValidationService).retrieveContext("POL-STD-999");
        
        // Verify no unexpected interactions (input validation/security check)
        verifyNoMoreInteractions(policyValidationService);
    }
}
