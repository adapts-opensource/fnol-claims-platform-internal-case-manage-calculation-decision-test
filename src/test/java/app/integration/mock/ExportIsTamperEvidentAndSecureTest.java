package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExportIsTamperEvidentAndSecureTest {

    private ExportTransformationService exportService;

    @Mock
    private DataValidationService validationService;

    @BeforeEach
    void setUp() {
        exportService = new ExportTransformationService(validationService);
    }

    @Test
    void export_is_tamper_evident_and_secure() throws NoSuchAlgorithmException {
        // Arrange
        Map<String, Object> originalData = Map.of(
            "reserve_id", "RES-001",
            "amount", 1500.00,
            "currency", "USD",
            "approval_status", "Approved",
            "secret_key", "DO_NOT_EXPORT"
        );

        // Act
        ExportResult result = exportService.transformAndSecureExport(originalData);

        // Assert - Tamper Evidence
        assertNotNull(result.signature(), "Export must include a tamper-evident signature");
        String expectedHash = computeHash(originalData);
        assertEquals(expectedHash, result.signature(), "Signature must cryptographically bind to the original payload");

        // Assert - Security & Compliance
        assertFalse(result.payload().containsKey("secret_key"), "Payload must sanitize sensitive fields");
        verify(validationService, times(1)).validate(any());
        assertTrue(result.payload().containsKey("reserve_id"), "Core business data must be preserved");
        assertFalse(result.payload().toString().contains("secret_key"), "No plaintext secrets in serialized output");
    }

    private String computeHash(Map<String, Object> data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.toString().getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }
}

record ExportResult(Map<String, Object> payload, String signature) {}

interface DataValidationService {
    void validate(Map<String, Object> data);
}

class ExportTransformationService {
    private final DataValidationService validationService;

    public ExportTransformationService(DataValidationService validationService) {
        this.validationService = validationService;
    }

    public ExportResult transformAndSecureExport(Map<String, Object> data) {
        validationService.validate(data);
        Map<String, Object> sanitizedPayload = data.entrySet().stream()
            .filter(entry -> !entry.getKey().startsWith("secret"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        try {
            String signature = computeTamperEvidentHash(sanitizedPayload);
            return new ExportResult(sanitizedPayload, signature);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unsupported hashing algorithm", e);
        }
    }

    private String computeTamperEvidentHash(Map<String, Object> data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.toString().getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }
}
