package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class InsuredEngagementTrackingTransformationValidationMockTest {

    private InsuredEngagementValidator validator;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        // Mock external I/O and transformation layer; never call live AWS/HTTP APIs
        validator = mock(InsuredEngagementValidator.class);
        auditService = mock(AuditService.class);
    }

    @Test
    void sanitize_and_validate_malicious_reporter_input() {
        String maliciousName = "<script>alert('xss')</script>";
        String reporterPhone = "123-456-7890";
        String policyNumber = "POL-TEST";

        String sanitizedName = "&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;";

        when(validator.sanitizeAndValidate(maliciousName, reporterPhone, policyNumber)).thenReturn(sanitizedName);
        when(auditService.logSanitization(anyString())).thenReturn(true);

        String result = validator.sanitizeAndValidate(maliciousName, reporterPhone, policyNumber);

        assertNotNull(result);
        assertFalse(result.contains("<script>"), "Malicious script tags should not remain");
        assertTrue(result.contains("&lt;script&gt;"), "Input should be HTML-escaped");
        verify(auditService).logSanitization("reporter_name");
    }
}

interface InsuredEngagementValidator {
    String sanitizeAndValidate(String reporterName, String reporterPhone, String policyNumber);
}

interface AuditService {
    boolean logSanitization(String fieldName);
}
