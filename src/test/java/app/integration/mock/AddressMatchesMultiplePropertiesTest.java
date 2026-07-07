package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AddressMatchesMultiplePropertiesTest {

    @Mock
    private PropertyLookupService propertyLookupService;

    private ClaimDataStandardizationValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new ClaimDataStandardizationValidator(propertyLookupService);
    }

    @Test
    void addressMatchesMultipleProperties() {
        // Given
        String testAddress = "123 Main St, Springfield, IL 62704";
        List<String> matchingPropertyIds = Arrays.asList("PROP_001", "PROP_002");
        when(propertyLookupService.findPropertyIdsByAddress(testAddress)).thenReturn(matchingPropertyIds);

        Map<String, Object> payload = Map.of("address", testAddress, "claimId", "CLM-999");
        ClaimDataStandardizationTransformationValida claimData = new ClaimDataStandardizationTransformationValida("CLM-999-STD", payload);

        // When
        ClaimValidationDecision decision = validator.validateDecision(claimData);

        // Then
        assertEquals(ClaimValidationDecision.MULTIPLE_MATCH, decision);
        verify(propertyLookupService).findPropertyIdsByAddress(testAddress);
    }

    enum ClaimValidationDecision { VALID, MULTIPLE_MATCH, NO_MATCH }

    interface PropertyLookupService {
        List<String> findPropertyIdsByAddress(String address);
    }

    static class ClaimDataStandardizationValidator {
        private final PropertyLookupService propertyLookupService;

        ClaimDataStandardizationValidator(PropertyLookupService propertyLookupService) {
            this.propertyLookupService = propertyLookupService;
        }

        ClaimValidationDecision validateDecision(ClaimDataStandardizationTransformationValida claimData) {
            String address = (String) claimData.getPayload().get("address");
            if (address == null || address.isBlank()) {
                return ClaimValidationDecision.NO_MATCH;
            }
            List<String> matches = propertyLookupService.findPropertyIdsByAddress(address);
            if (matches.size() > 1) {
                return ClaimValidationDecision.MULTIPLE_MATCH;
            }
            return matches.isEmpty() ? ClaimValidationDecision.NO_MATCH : ClaimValidationDecision.VALID;
        }
    }

    static class ClaimDataStandardizationTransformationValida {
        private final String id;
        private final Map<String, Object> payload;

        ClaimDataStandardizationTransformationValida(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }

        String getId() { return id; }
        Map<String, Object> getPayload() { return payload; }
    }
}
