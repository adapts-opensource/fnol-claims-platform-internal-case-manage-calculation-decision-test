package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementStateTransitionMockTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private CommunicationService communicationService;

    @Mock
    private DocumentStoreService documentStoreService;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(claimRepository, communicationService, documentStoreService);
    }

    @Test
    void input_criteria_policy_number_string_risk_address_structured_named_insured_string_date_of_loss_date_product_form_string_occupancy_type_string() {
        // Arrange: Input criteria matching test case label
        String policyNumber = "POL-2024-998877";
        RiskAddress riskAddress = new RiskAddress("100 Corporate Dr", "Metropolis", "NY", "10001");
        String namedInsured = "Clark Kent";
        String dateOfLoss = "2024-12-01";
        String productForm = "HO-3";
        String occupancyType = "Primary Residence";

        Claim mockClaim = new Claim(policyNumber, namedInsured);
        when(claimRepository.findByPolicyNumber(policyNumber)).thenReturn(java.util.Optional.of(mockClaim));

        // Act: Execute state transition with specified input criteria
        StateTransitionResult result = insuredEngagementService.evaluateAndTransition(
                policyNumber, riskAddress, namedInsured, dateOfLoss, productForm, occupancyType
        );

        // Assert: Verify state transition and mocked external I/O
        assertNotNull(result);
        assertEquals(EngagementState.ENGAAGEMENT_INITIATED, result.getCurrentState());
        assertTrue(result.isTransitionSuccessful());

        verify(claimRepository).findByPolicyNumber(policyNumber);
        verify(communicationService).sendEngagementNotification(eq(policyNumber), anyString());
        verify(documentStoreService).persistRiskData(eq(policyNumber), anyString());
    }

    // --- Internal Stub Definitions for Mocking External I/O ---
    static class Claim {
        final String policyNumber;
        final String namedInsured;
        Claim(String policyNumber, String namedInsured) {
            this.policyNumber = policyNumber;
            this.namedInsured = namedInsured;
        }
    }

    static class RiskAddress {
        final String street;
        final String city;
        final String state;
        final String zip;
        RiskAddress(String street, String city, String state, String zip) {
            this.street = street;
            this.city = city;
            this.state = state;
            this.zip = zip;
        }
    }

    record StateTransitionResult(EngagementState currentState, boolean transitionSuccessful) {}

    interface ClaimRepository {
        java.util.Optional<Claim> findByPolicyNumber(String policyNumber);
    }

    interface CommunicationService {
        void sendEngagementNotification(String policyNumber, String message);
    }

    interface DocumentStoreService {
        void persistRiskData(String policyNumber, String riskDataPayload);
    }

    class InsuredEngagementService {
        private final ClaimRepository claimRepository;
        private final CommunicationService communicationService;
        private final DocumentStoreService documentStoreService;

        InsuredEngagementService(ClaimRepository claimRepository, CommunicationService communicationService, DocumentStoreService documentStoreService) {
            this.claimRepository = claimRepository;
            this.communicationService = communicationService;
            this.documentStoreService = documentStoreService;
        }

        StateTransitionResult evaluateAndTransition(String policyNumber, RiskAddress riskAddress, String namedInsured, String dateOfLoss, String productForm, String occupancyType) {
            // Simulate validation and state transition logic
            claimRepository.findByPolicyNumber(policyNumber);
            communicationService.sendEngagementNotification(policyNumber, "Insured engagement initiated for " + namedInsured);
            documentStoreService.persistRiskData(policyNumber, String.format("risk:%s:%s", productForm, occupancyType));
            return new StateTransitionResult(EngagementState.ENGAAGEMENT_INITIATED, true);
        }
    }
}
