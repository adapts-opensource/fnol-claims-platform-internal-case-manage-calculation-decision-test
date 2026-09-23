package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    @Mock
    private AddressMatchValidator addressMatchValidator;

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    @BeforeEach
    void setUp() {
        // Mock external I/O: Address matching service
        when(addressMatchValidator.validateGeocode(anyString())).thenReturn(true);
        when(addressMatchValidator.validateAddressLine(anyString())).thenReturn(false);
    }

    @Test
    void address_match_requires_geocode_and_address_line_match() {
        // When: Calculate state transition with partial address match (geocode only)
        String actualState = stateTransitionCalculator.calculateTransition(
                "fnol-submission-42", "SUBMITTED", true, false
        );

        // Then: Assert that state transition calculation correctly requires both geocode and address line match
        assertEquals("PENDING_ADDRESS_VERIFICATION", actualState);
        verify(addressMatchValidator).validateGeocode(anyString());
        verify(addressMatchValidator).validateAddressLine(anyString());
        verifyNoMoreInteractions(addressMatchValidator);
    }
}
