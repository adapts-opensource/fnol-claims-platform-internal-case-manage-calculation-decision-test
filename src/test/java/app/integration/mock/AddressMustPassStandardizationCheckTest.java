package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that address standardization is enforced during insured engagement state transitions.
 * Aligns with NFRs: input_validation, security (PII sanitization during standardization), observability.
 */
@ExtendWith(MockitoExtension.class)
public class StateTransitionAddressStandardizationTest {

    @Mock
    private AddressStandardizationService addressStandardizationService;

    @Mock
    private StateTransitionEngine stateTransitionEngine;

    @Mock
    private EngagementStateRepository engagementStateRepository;

    private InsuredEngagementProcessor insuredEngagementProcessor;

    @BeforeEach
    void setUp() {
        insuredEngagementProcessor = new InsuredEngagementProcessor(
            addressStandardizationService,
            stateTransitionEngine,
            engagementStateRepository
        );
    }

    @Test
    void addressMustPassStandardizationCheck() {
        // Given
        String insuredId = "INS-12345";
        String rawAddress = "123 Main St, New York, NY 10001";
        StandardizedAddressDto standardizedAddress = new StandardizedAddressDto(
            "123 MAIN ST", "NEW YORK", "NY", "10001-1234", "US"
        );
        TransitionRequest request = new TransitionRequest(
            insuredId, "CLAIM_INITIATED", "UNDER_REVIEW", rawAddress
        );

        when(addressStandardizationService.standardize(rawAddress)).thenReturn(standardizedAddress);
        when(stateTransitionEngine.validateTransition(anyString(), anyString(), anyString())).thenReturn(true);
        doNothing().when(engagementStateRepository).save(any());

        // When
        boolean result = insuredEngagementProcessor.processStateTransition(request);

        // Then
        assertTrue(result, "Transition must succeed when address passes standardization");
        verify(addressStandardizationService).standardize(rawAddress);
        verify(stateTransitionEngine).validateTransition(eq(insuredId), eq("CLAIM_INITIATED"), eq("UNDER_REVIEW"));
        verify(engagementStateRepository).save(any());
        verifyNoMoreInteractions(addressStandardizationService, stateTransitionEngine, engagementStateRepository);
    }
}
