package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PolicyNumberOrRiskAddressNamedInsuredTest {

    @Mock
    private InsuredEngagementService insuredEngagementService;

    private StateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        stateTransitionService = new StateTransitionService(insuredEngagementService);
    }

    @Test
    void shouldTransitionStateWithPolicyNumberAndNamedInsured() {
        String policyNumber = "POL-12345";
        String namedInsured = "John Doe";
        String currentState = "PENDING";
        String expectedState = "ENGAGED";

        InsuredRecord mockRecord = new InsuredRecord("REC-001", currentState);
        when(insuredEngagementService.findByPolicyNumberAndInsured(eq(policyNumber), eq(namedInsured)))
                .thenReturn(mockRecord);

        String result = stateTransitionService.transition(policyNumber, null, namedInsured, currentState);

        assertEquals(expectedState, result);
        verify(insuredEngagementService).findByPolicyNumberAndInsured(eq(policyNumber), eq(namedInsured));
    }

    @Test
    void shouldTransitionStateWithRiskAddressAndNamedInsured() {
        String riskAddress = "123 Main St, Springfield";
        String namedInsured = "Jane Smith";
        String currentState = "PENDING";
        String expectedState = "ENGAGED";

        InsuredRecord mockRecord = new InsuredRecord("REC-002", currentState);
        when(insuredEngagementService.findByRiskAddressAndInsured(eq(riskAddress), eq(namedInsured)))
                .thenReturn(mockRecord);

        String result = stateTransitionService.transition(null, riskAddress, namedInsured, currentState);

        assertEquals(expectedState, result);
        verify(insuredEngagementService).findByRiskAddressAndInsured(eq(riskAddress), eq(namedInsured));
    }

    @Test
    void shouldFailWhenNeitherPolicyNorAddressProvided() {
        String namedInsured = "John Doe";
        assertThrows(IllegalArgumentException.class, () ->
                stateTransitionService.transition(null, null, namedInsured, "PENDING")
        );
    }

    @Test
    void shouldFailWhenNamedInsuredIsMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                stateTransitionService.transition("POL-123", "123 Main St", null, "PENDING")
        );
    }

    // Supporting domain and service classes for isolated unit testing
    static class InsuredEngagementService {
        public InsuredRecord findByPolicyNumberAndInsured(String policyNumber, String namedInsured) {
            return null;
        }
        public InsuredRecord findByRiskAddressAndInsured(String riskAddress, String namedInsured) {
            return null;
        }
    }

    static class StateTransitionService {
        private final InsuredEngagementService insuredEngagementService;

        StateTransitionService(InsuredEngagementService insuredEngagementService) {
            this.insuredEngagementService = insuredEngagementService;
        }

        String transition(String policyNumber, String riskAddress, String namedInsured, String currentState) {
            if (namedInsured == null || namedInsured.isBlank()) {
                throw new IllegalArgumentException("Named insured is required for state transition");
            }
            boolean hasPolicy = policyNumber != null && !policyNumber.isBlank();
            boolean hasAddress = riskAddress != null && !riskAddress.isBlank();

            if (!hasPolicy && !hasAddress) {
                throw new IllegalArgumentException("Either policy_number or risk_address is required");
            }

            InsuredRecord record = hasPolicy
                    ? insuredEngagementService.findByPolicyNumberAndInsured(policyNumber, namedInsured)
                    : insuredEngagementService.findByRiskAddressAndInsured(riskAddress, namedInsured);

            if (record == null) {
                throw new IllegalStateException("Insured record not found for provided identifiers");
            }

            // Simulate state transition logic
            return "ENGAGED";
        }
    }

    static class InsuredRecord {
        private final String id;
        private final String state;

        InsuredRecord(String id, String state) {
            this.id = id;
            this.state = state;
        }

        String getState() { return state; }
    }
}
