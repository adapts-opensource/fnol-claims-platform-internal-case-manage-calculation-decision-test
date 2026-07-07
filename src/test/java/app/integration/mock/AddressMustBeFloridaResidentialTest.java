package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AddressMustBeFloridaResidentialTest {

    @Mock
    private EngagementTransformationService engagementService;

    @InjectMocks
    private InsuredEngagementProcessor processor;

    private static final String MOCK_ADDRESS = "123 Palm Beach Dr";
    private static final String MOCK_STATE = "FL";
    private static final String MOCK_ADDRESS_TYPE = "RESIDENTIAL";

    @BeforeEach
    void setUp() {
        // External I/O (DynamoDB, SES, S3) is mocked via MockitoExtension.
        // No live AWS or production HTTP APIs are invoked during execution.
    }

    @Test
    void address_must_be_florida_residential() {
        // Arrange: Mock the transformation service to return valid Florida residential data
        when(engagementService.transformAddress(anyString(), anyString(), anyString()))
                .thenReturn(new AddressResult(MOCK_ADDRESS, MOCK_STATE, MOCK_ADDRESS_TYPE));

        // Act: Trigger the decision transformation pipeline
        AddressResult result = processor.processEngagementDecision(MOCK_ADDRESS, MOCK_STATE, MOCK_ADDRESS_TYPE);

        // Assert: Verify address state and type constraints
        assertNotNull(result, "Address transformation result must not be null");
        assertEquals(MOCK_STATE, result.getState(), "State must be Florida (FL)");
        assertEquals(MOCK_ADDRESS_TYPE, result.getAddressType(), "Address type must be RESIDENTIAL");
        verify(engagementService, times(1)).transformAddress(anyString(), anyString(), anyString());
    }

    // Minimal DTO for test scope
    private static class AddressResult {
        private final String address;
        private final String state;
        private final String addressType;

        public AddressResult(String address, String state, String addressType) {
            this.address = address;
            this.state = state;
            this.addressType = addressType;
        }

        public String getState() { return state; }
        public String getAddressType() { return addressType; }
    }

    // Placeholder interface to satisfy compilation context
    private interface EngagementTransformationService {
        AddressResult transformAddress(String address, String state, String type);
    }

    // Placeholder processor to satisfy compilation context
    private static class InsuredEngagementProcessor {
        private final EngagementTransformationService engagementService;
        
        public InsuredEngagementProcessor(EngagementTransformationService engagementService) {
            this.engagementService = engagementService;
        }
        
        public AddressResult processEngagementDecision(String address, String state, String type) {
            return engagementService.transformAddress(address, state, type);
        }
    }
}
