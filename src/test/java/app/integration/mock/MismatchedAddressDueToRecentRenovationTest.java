package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionValidationDecisionTest {

    @Mock
    private PolicyAddressService policyAddressService;

    @Mock
    private ExternalAddressValidator externalAddressValidator;

    @InjectMocks
    private FnolValidationDecisionEngine fnolValidationDecisionEngine;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and mock initialization
    }

    @Test
    void mismatched_address_due_to_recent_renovation() {
        // Arrange
        String policyId = "POL-882910";
        String submittedAddress = "742 Evergreen Terrace, Apt 4B";
        String registeredAddress = "742 Evergreen Terrace, Unit 1";

        when(policyAddressService.retrieveRegisteredAddress(policyId))
                .thenReturn(Optional.of(registeredAddress));
        when(externalAddressValidator.compare(submittedAddress, registeredAddress))
                .thenReturn(AddressComparisonResult.MISMATCH_RENOVATION);

        // Act
        ValidationDecision decision = fnolValidationDecisionEngine.evaluateSubmission(
                policyId, submittedAddress
        );

        // Assert
        assertEquals(ValidationDecision.REQUIRES_RENOVATION_VERIFICATION, decision);
        verify(policyAddressService).retrieveRegisteredAddress(policyId);
        verify(externalAddressValidator).compare(submittedAddress, registeredAddress);
    }
}
