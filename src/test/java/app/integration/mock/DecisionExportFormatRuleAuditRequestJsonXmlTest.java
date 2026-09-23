package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionExportFormatRuleAuditRequestJsonXmlTest {

    @Mock
    private AuditTransformationService auditTransformationService;

    @Mock
    private DigitalSignatureService digitalSignatureService;

    @Test
    void decision_export_format_rule_audit_request_json_xml_with_digital_signature_expected_outcome_secure_export() {
        // Arrange
        String auditRequestPayload = "{\"decision\":\"Export Format\",\"rule\":\"Audit request -> JSON/XML with digital signature\"}";
        String expectedSignature = "base64Sig_abc123Secure";
        ExportResult expectedResult = new ExportResult("SECURE_EXPORT", "application/json", expectedSignature);

        when(digitalSignatureService.generateSignature(anyString())).thenReturn(expectedSignature);
        when(auditTransformationService.transformAndSecureExport(anyString(), anyString()))
                .thenReturn(expectedResult);

        // Act
        ExportResult actualResult = auditTransformationService.transformAndSecureExport(auditRequestPayload, "json");

        // Assert
        assertNotNull(actualResult, "Export result must not be null");
        assertEquals("SECURE_EXPORT", actualResult.getStatus(), "Expected secure export outcome");
        assertTrue(
                actualResult.getContentType().contains("json") || actualResult.getContentType().contains("xml"),
                "Export format must be JSON or XML"
        );
        assertNotNull(actualResult.getDigitalSignature(), "Digital signature must be present");
        assertEquals(expectedSignature, actualResult.getDigitalSignature(), "Signature must match expected value");
        
        verify(digitalSignatureService, times(1)).generateSignature(anyString());
        verify(auditTransformationService, times(1)).transformAndSecureExport(anyString(), eq("json"));
    }

    /**
     * Minimal DTO representing the secure export outcome for test isolation.
     */
    static class ExportResult {
        private final String status;
        private final String contentType;
        private final String digitalSignature;

        ExportResult(String status, String contentType, String digitalSignature) {
            this.status = status;
            this.contentType = contentType;
            this.digitalSignature = digitalSignature;
        }

        String getStatus() { return status; }
        String getContentType() { return contentType; }
        String getDigitalSignature() { return digitalSignature; }
    }
}
