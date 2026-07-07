package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PolicyNumberMustMatchPasFormatRegexTest {

    private static final String PAS_POLICY_NUMBER_REGEX = "^[A-Z]{2,3}-\\d{6,10}$";
    private static final Pattern PAS_PATTERN = Pattern.compile(PAS_POLICY_NUMBER_REGEX);

    @Mock
    private ClaimDataRetrievalService claimDataRetrievalService;

    @Test
    void policy_number_must_match_pas_format_regex() {
        // Arrange: Mock external I/O contract for Claim Data Store
        Map<String, Object> validPayload = Map.of("policy_number", "ABC-123456", "claim_id", "clm-001");
        when(claimDataRetrievalService.fetchClaimData("clm-001")).thenReturn(validPayload);

        // Act: Extract policy number from payload and validate against PAS format regex
        String policyNumber = (String) claimDataRetrievalService.fetchClaimData("clm-001").get("policy_number");
        boolean matchesRegex = PAS_PATTERN.matcher(policyNumber).matches();

        // Assert: Verify policy_number matches expected PAS format
        assertTrue(matchesRegex, "policy_number must match PAS format regex");
    }

    @Test
    void policy_number_must_match_pas_format_regex_invalid_cases() {
        String[] invalidPolicyNumbers = {"12345", "AB-123", "AB 123456", "abc-123456", ""};

        for (String invalidPolicyNumber : invalidPolicyNumbers) {
            boolean matchesRegex = PAS_PATTERN.matcher(invalidPolicyNumber).matches();
            assertFalse(matchesRegex, "policy_number '" + invalidPolicyNumber + "' must match PAS format regex");
        }
    }

    @Test
    void policy_number_must_match_pas_format_regex_null_payload_handling() {
        when(claimDataRetrievalService.fetchClaimData("clm-002")).thenReturn(null);

        assertThrows(NullPointerException.class, () -> {
            Map<String, Object> payload = claimDataRetrievalService.fetchClaimData("clm-002");
            String policyNumber = (String) payload.get("policy_number");
            PAS_PATTERN.matcher(policyNumber).matches();
        }, "Null payload should be handled or throw during orchestration");
    }

    // Mock interface simulating external I/O contract (DynamoDB/S3 abstraction)
    interface ClaimDataRetrievalService {
        Map<String, Object> fetchClaimData(String claimId);
    }
}
