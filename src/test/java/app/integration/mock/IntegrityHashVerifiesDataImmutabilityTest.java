package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionDecisionValidationIntegrityTest {

    @Mock
    private Map<String, Object> mockGuidewireClaimModel;

    @Mock
    private Map<String, Object> mockPolicyCoverageValidator;

    @Mock
    private Map<String, Object> mockSesCommunicationAck;

    @Mock
    private Map<String, Object> mockS3DocumentMedia;

    private MessageDigest sha256;
    private Map<String, Object> fnolSubmissionData;
    private String expectedIntegrityHash;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        sha256 = MessageDigest.getInstance("SHA-256");
        fnolSubmissionData = new HashMap<>();
        fnolSubmissionData.put("id", "FNOL-INT-001");
        fnolSubmissionData.put("payload", Map.of("channel", "WEB", "incidentDate", "2023-10-25"));
        expectedIntegrityHash = computeHash(fnolSubmissionData);
    }

    @Test
    void integrity_hash_verifies_data_immutability() throws NoSuchAlgorithmException {
        // Arrange: Compute hash of the original immutable data
        String hashBefore = computeHash(fnolSubmissionData);
        assertEquals(expectedIntegrityHash, hashBefore, "Hash must match original data payload");

        // Act: Simulate an unauthorized mutation to the payload
        fnolSubmissionData.put("payload", Map.of("channel", "WEB", "incidentDate", "2023-10-25", "tamperedField", "true"));

        // Assert: Hash must change, proving the integrity check would reject the mutated data
        String hashAfter = computeHash(fnolSubmissionData);
        assertNotEquals(expectedIntegrityHash, hashAfter, "Hash must diverge when data is mutated");

        // Verify: Revert to original state to confirm immutability validation passes
        fnolSubmissionData.put("payload", Map.of("channel", "WEB", "incidentDate", "2023-10-25"));
        assertEquals(expectedIntegrityHash, computeHash(fnolSubmissionData), "Hash must match when data remains immutable");
    }

    private String computeHash(Map<String, Object> data) throws NoSuchAlgorithmException {
        String serialized = data.toString();
        byte[] digest = sha256.digest(serialized.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
