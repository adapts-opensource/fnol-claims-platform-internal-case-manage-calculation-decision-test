package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock tests for Claim Data Standardization Orchestration.
 * Validates transformation logic and infrastructure contract adherence.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentStoreClient documentStoreClient;

    @InjectMocks
    private ClaimDataStandardizationOrchestration orchestration;

    @Test
    void cause_of_loss_must_map_to_allowed_enumeration() {
        // Arrange
        String claimId = "CLM-12345";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("cause_of_loss", "Collision");
        inputPayload.put("policyId", "POL-999");

        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("cause_of_loss", "COLLISION"); // Mapped Enumeration
        expectedPayload.put("policyId", "POL-999");

        when(claimDataStoreClient.putItem(anyString(), anyMap())).thenReturn(Optional.of(Map.of("id", claimId)));

        // Act
        Map<String, Object> result = orchestration.standardizeClaimData(claimId, inputPayload);

        // Assert
        assertNotNull(result, "Result payload should not be null");
        assertEquals("COLLISION", result.get("cause_of_loss"),
            "Cause of loss must be mapped to the allowed enumeration value.");

        // Verify infrastructure interactions
        verify(claimDataStoreClient, times(1)).putItem(anyString(), anyMap());
    }
}
