package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimStandardizationDecisionTest {

    @Mock
    private FnolIntakeService fnolIntakeService;

    private StandardizationDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new StandardizationDecisionEngine(fnolIntakeService);
    }

    @Test
    void applies_when_fnol_intake_received() {
        // Arrange
        String claimId = "claim-123";
        String tenantId = "tenant-456";
        String policyId = "policy-789";
        String claimNumber = "CLM-001";

        when(fnolIntakeService.getIntakeStatus(claimId)).thenReturn(IntakeStatus.RECEIVED);

        Claim claim = new Claim(claimId, claimNumber, tenantId, policyId);

        // Act
        boolean applies = decisionEngine.shouldApplyStandardization(claim);

        // Assert
        assertTrue(applies, "Standardization decision should apply when FNOL intake is received");
        verify(fnolIntakeService, times(1)).getIntakeStatus(claimId);
    }

    enum IntakeStatus { RECEIVED, PROCESSING, COMPLETED }

    static class Claim {
        private final String claimId;
        private final String claimNumber;
        private final String tenantId;
        private final String policyId;

        Claim(String claimId, String claimNumber, String tenantId, String policyId) {
            this.claimId = claimId;
            this.claimNumber = claimNumber;
            this.tenantId = tenantId;
            this.policyId = policyId;
        }

        String getClaimId() { return claimId; }
    }

    interface FnolIntakeService {
        IntakeStatus getIntakeStatus(String claimId);
    }

    static class StandardizationDecisionEngine {
        private final FnolIntakeService fnolIntakeService;

        StandardizationDecisionEngine(FnlIntakeService fnolIntakeService) {
            this.fnolIntakeService = fnolIntakeService;
        }

        boolean shouldApplyStandardization(Claim claim) {
            return IntakeStatus.RECEIVED.equals(fnolIntakeService.getIntakeStatus(claim.getClaimId()));
        }
    }
}
