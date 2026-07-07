package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PiiRedactionFailureBlockExportAlertSecurityTest {

    @Mock
    private PiiRedactionService piiRedactionService;

    @Mock
    private ExportService exportService;

    @Mock
    private SecurityAlertService securityAlertService;

    @InjectMocks
    private InsuredEngagementExportService insuredEngagementExportService;

    @Test
    void pii_redaction_failure_block_export_alert_security() {
        // Given
        String insuredPayload = "{\"claimId\":\"CLM-001\",\"insuredName\":\"Jane Doe\",\"ssn\":\"987-65-4321\"}";
        doThrow(new RuntimeException("PII redaction failed")).when(piiRedactionService).redact(insuredPayload);

        // When & Then
        assertThrows(RuntimeException.class, () -> insuredEngagementExportService.transformAndExport(insuredPayload));

        // Verify export was strictly blocked
        verify(exportService, never()).exportData(anyString());
        // Verify security alert was triggered with context
        verify(securityAlertService, times(1)).alertSecurity("PII redaction failure detected. Export blocked.");
    }

    // Minimal interfaces/classes for test compilation context
    interface PiiRedactionService {
        void redact(String data) throws RuntimeException;
    }

    interface ExportService {
        void exportData(String data);
    }

    interface SecurityAlertService {
        void alertSecurity(String message);
    }

    static class InsuredEngagementExportService {
        private final PiiRedactionService piiRedactionService;
        private final ExportService exportService;
        private final SecurityAlertService securityAlertService;

        InsuredEngagementExportService(PiiRedactionService piiRedactionService, ExportService exportService, SecurityAlertService securityAlertService) {
            this.piiRedactionService = piiRedactionService;
            this.exportService = exportService;
            this.securityAlertService = securityAlertService;
        }

        void transformAndExport(String payload) {
            piiRedactionService.redact(payload);
            exportService.exportData(payload);
        }
    }
}
