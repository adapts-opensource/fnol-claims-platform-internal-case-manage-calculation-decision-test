package app.integration.mock;

import app.domain.claim.ClaimInput;
import app.domain.claim.ClaimRecord;
import app.domain.decision.DecisionResult;
import app.service.claim.ClaimDecisionService;
import app.repository.claim.ClaimsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock tests for Internal Case Management: Calculation Decision.
 * Verifies comparison logic against open/recent claims for duplicate detection and risk assessment.
 */
public class InternalCaseManagementCalculationDecisionMockTest {

    @Mock
    private ClaimsRepository claimsRepository;

    @InjectMocks
    private ClaimDecisionService claimDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void description_compares_policy_number_risk_address_date_of_loss_cause_of_loss_catastrophe_event_reporter_damaged_area_and_prior_claim_status_against_open_recent_claims() {
        // Arrange
        String policyNumber = "POL-882910";
        String riskAddress = "100 Thunder Road";
        LocalDate dateOfLoss = LocalDate.of(2023, 11, 15);
        String causeOfLoss = "Windstorm";
        String catastropheEvent = "Hurricane_Elena";
        String reporter = "Jane Smith";
        String damagedArea = "Structural_Roof";
        String priorClaimStatus = "Open";

        ClaimInput input = new ClaimInput(policyNumber, riskAddress, dateOfLoss, causeOfLoss, catastropheEvent, reporter, damagedArea, priorClaimStatus);

        // Mock existing open/recent claims to simulate comparison target
        ClaimRecord existingClaim = new ClaimRecord("REC-001", policyNumber, riskAddress, dateOfLoss, causeOfLoss, catastropheEvent, reporter, damagedArea, "Open");
        List<ClaimRecord> openRecentClaims = List.of(existingClaim);

        when(claimsRepository.findOpenRecentClaims()).thenReturn(openRecentClaims);

        // Act
        DecisionResult result = claimDecisionService.calculateDecision(input);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertTrue(result.isDuplicateMatchFound(), "Should detect match based on policy number, address, and date of loss");
        assertEquals("HIGH_RISK_DUPLICATE", result.getRiskFlag(), "Risk flag should indicate high risk due to open claim match");
        
        verify(claimsRepository, times(1)).findOpenRecentClaims();
        verifyNoMoreInteractions(claimsRepository);
    }
}
