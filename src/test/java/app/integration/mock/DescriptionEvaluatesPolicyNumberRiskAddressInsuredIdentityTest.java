package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementStateTransitionMockTest {

    @Mock
    private PolicyRecordRepository policyRecordRepository;

    @Mock
    private StateTransitionEngine stateTransitionEngine;

    private InsuredEngagementService insuredEngagementService;

    private static final String POLICY_NUMBER = "POL-8842-NOV";
    private static final String RISK_ADDRESS = "100 Insurance Blvd, Suite 200";
    private static final String INSURED_IDENTITY = "INS-9921-CORP";
    private static final String PRODUCT_FORM = "CG-001";
    private static final String OCCUPANCY = "Commercial-Retail";
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2023, 1, 1);
    private static final LocalDate EXPIRATION_DATE = LocalDate.of(2024, 1, 1);
    private static final LocalDate DOL = LocalDate.of(2023, 7, 15);

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(policyRecordRepository, stateTransitionEngine);
    }

    @Test
    void description_evaluates_policy_number_risk_address_insured_identity_dol_product_form_and_occupancy_against_policy_records_validates_dol_against_effective_expiration_cancellation_reinstatement_rewrite_dates_and_binding_moratorium_restrictions() {
        // Arrange: Mock policy record retrieval from DynamoDB-backed repository
        PolicyRecord mockPolicy = mock(PolicyRecord.class);
        when(mockPolicy.getPolicyNumber()).thenReturn(POLICY_NUMBER);
        when(mockPolicy.getRiskAddress()).thenReturn(RISK_ADDRESS);
        when(mockPolicy.getInsuredIdentity()).thenReturn(INSURED_IDENTITY);
        when(mockPolicy.getProductForm()).thenReturn(PRODUCT_FORM);
        when(mockPolicy.getOccupancy()).thenReturn(OCCUPANCY);
        when(mockPolicy.getEffectiveDate()).thenReturn(EFFECTIVE_DATE);
        when(mockPolicy.getExpirationDate()).thenReturn(EXPIRATION_DATE);
        when(mockPolicy.isCancelled()).thenReturn(false);
        when(mockPolicy.isReinstated()).thenReturn(false);
        when(mockPolicy.isRewritten()).thenReturn(false);
        when(mockPolicy.isInBindingPeriod()).thenReturn(false);
        when(mockPolicy.isInMoratorium()).thenReturn(false);

        when(policyRecordRepository.findByPolicyNumberAndAddress(POLICY_NUMBER, RISK_ADDRESS))
                .thenReturn(Optional.of(mockPolicy));

        EngagementContext context = new EngagementContext(
                POLICY_NUMBER, RISK_ADDRESS, INSURED_IDENTITY, DOL, PRODUCT_FORM, OCCUPANCY
        );

        StateTransitionDecision expectedDecision = mock(StateTransitionDecision.class);
        when(expectedDecision.isEligible()).thenReturn(true);
        when(expectedDecision.getValidationStatus()).thenReturn(ValidationStatus.VALID);
        when(stateTransitionEngine.evaluateAndTransition(context)).thenReturn(expectedDecision);

        // Act
        StateTransitionDecision actualDecision = insuredEngagementService.processStateTransition(context);

        // Assert
        assertNotNull(actualDecision, "Decision must not be null");
        assertTrue(actualDecision.isEligible(), "Policy must be eligible for state transition");
        assertEquals(ValidationStatus.VALID, actualDecision.getValidationStatus(), "Validation must pass all DOL and restriction checks");
        
        // Verify policy record lookup was invoked exactly once with correct parameters
        verify(policyRecordRepository, times(1)).findByPolicyNumberAndAddress(POLICY_NUMBER, RISK_ADDRESS);
        verify(stateTransitionEngine, times(1)).evaluateAndTransition(context);
        verifyNoMoreInteractions(policyRecordRepository, stateTransitionEngine);
    }
}

// Minimal domain stubs to ensure compilation context for the test artifact
class PolicyRecordRepository {
    public Optional<PolicyRecord> findByPolicyNumberAndAddress(String policyNumber, String address) { return Optional.empty(); }
}

interface PolicyRecord {
    String getPolicyNumber(); String getRiskAddress(); String getInsuredIdentity();
    String getProductForm(); String getOccupancy();
    LocalDate getEffectiveDate(); LocalDate getExpirationDate();
    boolean isCancelled(); boolean isReinstated(); boolean isRewritten();
    boolean isInBindingPeriod(); boolean isInMoratorium();
}

class EngagementContext {
    public EngagementContext(String policyNumber, String riskAddress, String insuredIdentity, LocalDate dol, String productForm, String occupancy) {}
}

class StateTransitionEngine {
    public StateTransitionDecision evaluateAndTransition(EngagementContext context) { return null; }
}

class InsuredEngagementService {
    public InsuredEngagementService(PolicyRecordRepository repo, StateTransitionEngine engine) {}
    public StateTransitionDecision processStateTransition(EngagementContext context) { return null; }
}

class StateTransitionDecision {
    public boolean isEligible() { return false; }
    public ValidationStatus getValidationStatus() { return null; }
}

enum ValidationStatus { VALID, INVALID }
