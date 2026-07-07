package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private PolicyValidationService policyValidationServiceMock;

    @InjectMocks
    private ClaimDataStandardizationService claimDataStandardizationService;

    @BeforeEach
    void setUp() {
        // Initialization for mocks or service setup if required
    }

    @Test
    void policy_system_returns_stale_data() {
        // Arrange
        String claimId = "CLM-STALE-001";
        Map<String, Object> stalePolicyData = Map.of(
            "policyId", "POL-999",
            "status", "ACTIVE",
            "lastUpdated", "2020-01-01T00:00:00Z",
            "version", "1.0"
        );

        when(policyValidationServiceMock.getPolicyData(claimId))
            .thenReturn(stalePolicyData);

        // Act
        var decisionResult = claimDataStandardizationService.validateDecision(claimId);

        // Assert
        assertNotNull(decisionResult, "Decision result should not be null");
        assertEquals("STALE_DATA", decisionResult.getValidationOutcome(), 
            "Should detect stale data from policy system");
        assertEquals(claimId, decisionResult.getClaimId());

        // Verify
        verify(policyValidationServiceMock, times(1)).getPolicyData(claimId);
        verifyNoMoreInteractions(policyValidationServiceMock);
    }
}
